package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Durable subscriber to congestion-topic that keeps a {@link CongestionCache} up to date.
 *
 * <p><b>Durable</b> means the broker remembers this subscription (client id + subscription name) while we are
 * offline and replays what we missed on reconnect; a plain topic subscriber silently loses every update
 * published during a restart.
 *
 * <p>If the broker is unreachable at start-up, or the connection later drops, a background thread keeps retrying,
 * so the REST API stays up the whole time.
 */
public final class CongestionSubscriber implements AutoCloseable {

    public static final String CLIENT_ID = "routing-service";
    public static final String SUBSCRIPTION_NAME = "routing-service-congestion";

    private final ActiveMQConnectionFactory factory;
    private final CongestionCache cache;
    private final long retryMillis;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Connection connection;
    private volatile boolean closed;

    public CongestionSubscriber(String brokerUrl, CongestionCache cache) {
        this(brokerUrl, cache, 5000);
    }

    public CongestionSubscriber(String brokerUrl, CongestionCache cache, long retryMillis) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.cache = cache;
        this.retryMillis = retryMillis;
    }

    /** Connects in the background; returns immediately. */
    public void start() {
        Thread thread = new Thread(this::connectWithRetry, "congestion-subscriber");
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
                System.err.println("routing-service: broker not reachable (" + e.getMessage()
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
            c.setClientID(CLIENT_ID);                          // must be set before anything else
            c.setExceptionListener(e -> onConnectionLost(c, e));
            Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createDurableSubscriber(topic, SUBSCRIPTION_NAME);
            consumer.setMessageListener(this::onMessage);
            c.start();
            connection = c;
            System.out.println("routing-service subscribed to " + MqConfig.TOPIC);
        } catch (JMSException e) {
            closeQuietly(c);
            throw e;
        }
    }

    private void onConnectionLost(Connection lost, JMSException cause) {
        if (closed) {
            return;
        }
        System.err.println("routing-service lost the broker connection: " + cause.getMessage());
        connection = null;
        closeQuietly(lost);
        Thread reconnect = new Thread(this::connectWithRetry, "congestion-subscriber-reconnect");
        reconnect.setDaemon(true);
        reconnect.start();
    }

    /** Never throws: a bad message must not be redelivered forever. */
    void onMessage(Message message) {
        try {
            if (!(message instanceof TextMessage text)) {
                System.err.println("routing-service ignored a non-text message");
                return;
            }
            CongestionChange change = mapper.readValue(text.getText(), CongestionChange.class);
            if (change.level() == null || change.level() < 0 || change.level() > 8) {
                System.err.println("routing-service ignored an invalid congestion message: " + text.getText());
                return;
            }
            cache.update(change.level());
            System.out.println("routing-service cached congestion level " + change.level());
        } catch (Exception e) {
            System.err.println("routing-service ignored a malformed message: " + e.getMessage());
        }
    }

    /** Disconnects but keeps the durable subscription registered, so the broker holds messages for us. */
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
            // already broken - nothing to do
        }
    }
}
