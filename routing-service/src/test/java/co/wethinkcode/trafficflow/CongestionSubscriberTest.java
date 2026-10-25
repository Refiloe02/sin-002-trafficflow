package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.wethinkcode.trafficflow.mq.MqConfig;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real in-JVM ActiveMQ broker on a random TCP port (no Docker, no clash with a local broker). */
class CongestionSubscriberTest {

    private BrokerService broker;
    private String brokerUrl;

    @BeforeEach
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setBrokerName("routing-test");
        brokerUrl = broker.addConnector("tcp://127.0.0.1:0").getPublishableConnectString();
        broker.start();
        broker.waitUntilStarted();
    }

    @AfterEach
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    private void publish(String json) throws Exception {
        Connection c = new ActiveMQConnectionFactory(brokerUrl).createConnection();
        try {
            Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer p = s.createProducer(s.createTopic(MqConfig.TOPIC));
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
    void aPublishedMessageUpdatesTheCache() throws Exception {
        CongestionCache cache = new CongestionCache();
        try (CongestionSubscriber subscriber = new CongestionSubscriber(brokerUrl, cache, 100)) {
            subscriber.start();
            waitUntil(subscriber::isConnected);

            publish("{\"level\":5,\"timestamp\":\"2026-07-18T10:15:00Z\"}");

            waitUntil(() -> cache.level() == 5);
        }
    }

    @Test
    void messagesPublishedWhileOfflineAreDeliveredOnReconnect() throws Exception {
        CongestionCache cache = new CongestionCache();
        CongestionSubscriber first = new CongestionSubscriber(brokerUrl, cache, 100);
        first.start();
        waitUntil(first::isConnected);
        first.close();                                          // routing-service goes down...

        publish("{\"level\":7,\"timestamp\":\"2026-07-18T11:00:00Z\"}");   // ...while this is sent
        assertEquals(0, cache.level());

        try (CongestionSubscriber second = new CongestionSubscriber(brokerUrl, cache, 100)) {   // ...and comes back
            second.start();
            waitUntil(() -> cache.level() == 7);
        }
    }

    @Test
    void malformedMessagesAreIgnoredAndTheSubscriberKeepsWorking() throws Exception {
        CongestionCache cache = new CongestionCache();
        try (CongestionSubscriber subscriber = new CongestionSubscriber(brokerUrl, cache, 100)) {
            subscriber.start();
            waitUntil(subscriber::isConnected);

            publish("not json at all");
            publish("{\"level\":99}");                          // out of range
            publish("{\"level\":4}");

            waitUntil(() -> cache.level() == 4);
        }
    }
}
