package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Listens on two queues:
 * <ul>
 *   <li>{@code intersection-heartbeat-queue} - every message is proof of life</li>
 *   <li>{@code ActiveMQ.DLQ} - a heartbeat that expired unconsumed (the watchdog or broker was away) is reported</li>
 * </ul>
 * Connects in the background and keeps retrying, so the watchdog's own REST API stays up whatever the broker does.
 */
public final class HeartbeatListener implements AutoCloseable {

    private final ActiveMQConnectionFactory factory;
    private final WatchdogService watchdog;
    private final long retryMillis;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Connection connection;
    private volatile boolean closed;

    public HeartbeatListener(String brokerUrl, WatchdogService watchdog, long retryMillis) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.watchdog = watchdog;
        this.retryMillis = retryMillis;
    }

    public void start() {
        Thread thread = new Thread(this::connectWithRetry, "heartbeat-listener");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isConnected() {
        return connection != null;
    }

    private void connectWithRetry() {
        while (!closed) {
            try {
                connectOnce();
                return;
            } catch (JMSException e) {
                System.err.println("intersection-watchdog: broker not reachable (" + e.getMessage()
                        + "), retrying in " + retryMillis + " ms");
                try {
                    Thread.sleep(retryMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void connectOnce() throws JMSException {
        Connection c = factory.createConnection();
        try {
            c.setExceptionListener(e -> onConnectionLost(c, e));
            Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer beats = session.createConsumer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
            beats.setMessageListener(this::onHeartbeat);
            MessageConsumer dead = session.createConsumer(session.createQueue(MqConfig.DEAD_LETTER_QUEUE));
            dead.setMessageListener(this::onDeadLetter);
            c.start();
            connection = c;
            watchdog.onBrokerConnected();
            System.out.println("intersection-watchdog listening on " + MqConfig.HEARTBEAT_QUEUE + " and " + MqConfig.DEAD_LETTER_QUEUE);
        } catch (JMSException e) {
            closeQuietly(c);
            throw e;
        }
    }

    private void onConnectionLost(Connection lost, JMSException cause) {
        if (closed) {
            return;
        }
        System.err.println("intersection-watchdog lost the broker connection: " + cause.getMessage());
        connection = null;
        closeQuietly(lost);
        Thread reconnect = new Thread(this::connectWithRetry, "heartbeat-listener-reconnect");
        reconnect.setDaemon(true);
        reconnect.start();
    }

    /** Never throws: a bad message must not be redelivered forever. */
    void onHeartbeat(Message message) {
        try {
            if (message instanceof TextMessage text) {
                Heartbeat heartbeat = mapper.readValue(text.getText(), Heartbeat.class);
                if (WATCHED_SERVICE.equals(heartbeat.service())) {
                    watchdog.onHeartbeat();
                }
            }
        } catch (Exception e) {
            System.err.println("intersection-watchdog ignored a malformed heartbeat: " + e.getMessage());
        }
    }

    /** The dead-letter queue can hold anything; only heartbeats from intersection-service are our business. */
    void onDeadLetter(Message message) {
        try {
            if (message instanceof TextMessage text) {
                Heartbeat heartbeat = mapper.readValue(text.getText(), Heartbeat.class);
                if (WATCHED_SERVICE.equals(heartbeat.service())) {
                    watchdog.onDeadLettered("sequence " + heartbeat.sequence() + " sent at " + heartbeat.timestamp());
                }
            }
        } catch (Exception e) {
            // not one of our heartbeats - ignore
        }
    }

    private static final String WATCHED_SERVICE = "intersection-service";

    @Override
    public void close() {
        closed = true;
        Connection c = connection;
        connection = null;
        if (c != null) {
            closeQuietly(c);
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (JMSException ignored) {
            // already broken
        }
    }
}
