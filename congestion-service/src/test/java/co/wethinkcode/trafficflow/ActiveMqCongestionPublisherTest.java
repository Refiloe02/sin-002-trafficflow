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

/** Proves a message really travels through a broker. Uses an embedded in-JVM broker (vm://), no Docker needed. */
class ActiveMqCongestionPublisherTest {

    private static final String EMBEDDED = "vm://congestion-test?broker.persistent=false&broker.useJmx=false";

    @Test
    void publishedChangeArrivesOnTheCongestionTopic() throws Exception {
        Connection connection = new ActiveMQConnectionFactory(EMBEDDED).createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer subscriber = session.createConsumer(session.createTopic(MqConfig.TOPIC));

            try (ActiveMqCongestionPublisher publisher = new ActiveMqCongestionPublisher(EMBEDDED)) {
                publisher.publish(new CongestionChange(6, "2026-07-18T10:15:00Z"));
            }

            TextMessage received = (TextMessage) subscriber.receive(5000);
            assertNotNull(received, "no message arrived on " + MqConfig.TOPIC);
            JsonNode json = new ObjectMapper().readTree(received.getText());
            assertEquals(6, json.get("level").asInt());
            assertEquals("2026-07-18T10:15:00Z", json.get("timestamp").asText());
        } finally {
            connection.close();
        }
    }
}
