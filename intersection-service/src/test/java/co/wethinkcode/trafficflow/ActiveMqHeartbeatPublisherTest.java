package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

/** A heartbeat really lands on the queue. Uses an embedded in-JVM broker (vm://), no Docker needed. */
class ActiveMqHeartbeatPublisherTest {

    private static final String EMBEDDED = "vm://heartbeat-test?broker.persistent=false&broker.useJmx=false";

    @Test
    void heartbeatArrivesOnTheHeartbeatQueue() throws Exception {
        Connection connection = new ActiveMQConnectionFactory(EMBEDDED).createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));

            try (ActiveMqHeartbeatPublisher publisher = new ActiveMqHeartbeatPublisher(EMBEDDED, 15_000)) {
                publisher.publish(new Heartbeat("intersection-service", 7, "2026-07-18T10:15:00Z"));
            }

            TextMessage received = (TextMessage) consumer.receive(5000);
            assertNotNull(received, "no heartbeat arrived on " + MqConfig.HEARTBEAT_QUEUE);
            JsonNode json = new ObjectMapper().readTree(received.getText());
            assertEquals("intersection-service", json.get("service").asText());
            assertEquals(7, json.get("sequence").asInt());
        } finally {
            connection.close();
        }
    }
}
