package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.wethinkcode.trafficflow.mq.MqConfig;
import java.time.Clock;
import java.time.Duration;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real in-JVM ActiveMQ broker on a random TCP port: heartbeat queue + dead-letter queue behaviour. */
class HeartbeatListenerTest {

    private BrokerService broker;
    private String brokerUrl;

    @BeforeEach
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setBrokerName("watchdog-test");
        brokerUrl = broker.addConnector("tcp://127.0.0.1:0").getPublishableConnectString();
        broker.start();
        broker.waitUntilStarted();
    }

    @AfterEach
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    private void send(String queue, String json) throws Exception {
        Connection c = new ActiveMQConnectionFactory(brokerUrl).createConnection();
        try {
            Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer p = s.createProducer(s.createQueue(queue));
            p.send(s.createTextMessage(json));
        } finally {
            c.close();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within 5s");
    }

    @Test
    void aHeartbeatOnTheQueueMakesTheServiceUp() throws Exception {
        WatchdogService watchdog = new WatchdogService(Duration.ofSeconds(15), Clock.systemUTC());
        try (HeartbeatListener listener = new HeartbeatListener(brokerUrl, watchdog, 100)) {
            listener.start();
            waitUntil(listener::isConnected);

            send(MqConfig.HEARTBEAT_QUEUE, "{\"service\":\"intersection-service\",\"sequence\":1,\"timestamp\":\"t\"}");

            waitUntil(() -> "UP".equals(watchdog.status().state()));
        } finally {
            watchdog.close();
        }
    }

    @Test
    void heartbeatsFromOtherServicesAndGarbageAreIgnored() throws Exception {
        WatchdogService watchdog = new WatchdogService(Duration.ofSeconds(15), Clock.systemUTC());
        try (HeartbeatListener listener = new HeartbeatListener(brokerUrl, watchdog, 100)) {
            listener.start();
            waitUntil(listener::isConnected);

            send(MqConfig.HEARTBEAT_QUEUE, "not json");
            send(MqConfig.HEARTBEAT_QUEUE, "{\"service\":\"some-other-service\",\"sequence\":1}");
            send(MqConfig.HEARTBEAT_QUEUE, "{\"service\":\"intersection-service\",\"sequence\":2,\"timestamp\":\"t\"}");

            waitUntil(() -> "UP".equals(watchdog.status().state()));
        } finally {
            watchdog.close();
        }
    }

    @Test
    void aHeartbeatInTheDeadLetterQueueRaisesAnAlert() throws Exception {
        WatchdogService watchdog = new WatchdogService(Duration.ofSeconds(15), Clock.systemUTC());
        try (HeartbeatListener listener = new HeartbeatListener(brokerUrl, watchdog, 100)) {
            listener.start();
            waitUntil(listener::isConnected);

            send(MqConfig.DEAD_LETTER_QUEUE, "{\"service\":\"intersection-service\",\"sequence\":9,\"timestamp\":\"2026-07-18T10:15:00Z\"}");

            waitUntil(() -> !watchdog.status().alerts().isEmpty());
            assertEquals("HEARTBEAT_DEAD_LETTERED", watchdog.status().alerts().get(0).kind());
        } finally {
            watchdog.close();
        }
    }

    @Test
    void silenceAfterHeartbeatsTurnsIntoADownAlert() throws Exception {
        WatchdogService watchdog = new WatchdogService(Duration.ofSeconds(1), Clock.systemUTC());
        watchdog.start();
        try (HeartbeatListener listener = new HeartbeatListener(brokerUrl, watchdog, 100)) {
            listener.start();
            waitUntil(listener::isConnected);
            send(MqConfig.HEARTBEAT_QUEUE, "{\"service\":\"intersection-service\",\"sequence\":1,\"timestamp\":\"t\"}");
            waitUntil(() -> "UP".equals(watchdog.status().state()));

            // ...and then the heartbeats stop (intersection-service "crashed")
            waitUntil(() -> "DOWN".equals(watchdog.status().state()));
            assertEquals("HEARTBEAT_MISSED", watchdog.status().alerts().get(0).kind());
        } finally {
            watchdog.close();
        }
    }
}
