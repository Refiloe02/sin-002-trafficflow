package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeartbeatSenderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T10:15:00.500Z"), ZoneOffset.UTC);

    @Test
    void beatsCarryIncreasingSequenceNumbersAndTheServiceName() {
        List<Heartbeat> sent = new CopyOnWriteArrayList<>();
        HeartbeatSender sender = new HeartbeatSender(sent::add, CLOCK, 1000);

        sender.beat();
        sender.beat();

        assertEquals(List.of(
                new Heartbeat("intersection-service", 1, "2026-07-18T10:15:00Z"),
                new Heartbeat("intersection-service", 2, "2026-07-18T10:15:00Z")), sent);
    }

    @Test
    void aFailedSendDoesNotStopLaterBeats() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<Heartbeat> delivered = new CopyOnWriteArrayList<>();
        HeartbeatPublisher flaky = beat -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("broker down");
            }
            delivered.add(beat);
        };

        try (HeartbeatSender sender = new HeartbeatSender(flaky, CLOCK, 20)) {
            sender.start();
            long deadline = System.currentTimeMillis() + 3000;
            while (delivered.size() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        }
        assertTrue(delivered.size() >= 2, "scheduler should keep beating after a failure");
        assertEquals(2, delivered.get(0).sequence());   // beat #1 failed, #2 was the first delivered
    }
}
