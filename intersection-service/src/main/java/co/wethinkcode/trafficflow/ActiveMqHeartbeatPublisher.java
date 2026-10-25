package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Sends each {@link Heartbeat} to the {@code intersection-heartbeat-queue} QUEUE (point-to-point: exactly one
 * consumer, the watchdog, receives each beat).
 *
 * <p>Heartbeats are NON_PERSISTENT (a stale beat is worthless after a broker restart) and carry a time-to-live.
 * If nobody consumes a beat before it expires, ActiveMQ moves it to the dead-letter queue, which is the second
 * signal the watchdog listens for.
 */
public final class ActiveMqHeartbeatPublisher implements HeartbeatPublisher, AutoCloseable {

    private final ActiveMQConnectionFactory factory;
    private final long timeToLiveMillis;
    private final ObjectMapper mapper = new ObjectMapper();
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    public ActiveMqHeartbeatPublisher(String brokerUrl, long timeToLiveMillis) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.timeToLiveMillis = timeToLiveMillis;
    }

    @Override
    public synchronized void publish(Heartbeat heartbeat) throws JMSException, JsonProcessingException {
        String json = mapper.writeValueAsString(heartbeat);
        try {
            ensureConnected();
            producer.send(session.createTextMessage(json));
        } catch (JMSException e) {
            close();                    // force a fresh connection on the next beat
            throw e;
        }
    }

    private void ensureConnected() throws JMSException {
        if (connection != null) {
            return;
        }
        Connection c = factory.createConnection();
        try {
            c.start();
            session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            producer = session.createProducer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            producer.setTimeToLive(timeToLiveMillis);
            connection = c;
        } catch (JMSException e) {
            try {
                c.close();
            } catch (JMSException ignored) {
                // nothing more we can do
            }
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException ignored) {
            // discarding a broken connection
        } finally {
            connection = null;
            session = null;
            producer = null;
        }
    }
}
