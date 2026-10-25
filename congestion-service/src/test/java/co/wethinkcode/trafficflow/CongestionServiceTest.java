package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CongestionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T10:15:00.789Z"), ZoneOffset.UTC);

    private final CongestionStore store = new CongestionStore();
    private final List<CongestionChange> sent = new ArrayList<>();

    private CongestionService serviceWith(CongestionPublisher publisher) {
        return new CongestionService(store, publisher, CLOCK);
    }

    @Test
    void aChangePublishesExactlyOneMessage() {
        CongestionService.Update update = serviceWith(sent::add).update(4);

        assertTrue(update.changed());
        assertTrue(update.published());
        assertEquals(List.of(new CongestionChange(4, "2026-07-18T10:15:00Z")), sent);
    }

    @Test
    void settingTheSameLevelAgainPublishesNothing() {
        CongestionService service = serviceWith(sent::add);
        service.update(4);
        CongestionService.Update again = service.update(4);

        assertFalse(again.changed());
        assertFalse(again.published());
        assertEquals(1, sent.size());
    }

    @Test
    void brokerOutageDoesNotFailTheUpdate() {
        CongestionService.Update update = serviceWith(change -> {
            throw new IllegalStateException("broker down");
        }).update(3);

        assertTrue(update.changed());
        assertFalse(update.published());
        assertEquals(3, store.get());
    }

    @Test
    void invalidLevelIsRejectedAndNothingIsPublished() {
        assertThrows(IllegalArgumentException.class, () -> serviceWith(sent::add).update(9));
        assertTrue(sent.isEmpty());
    }
}
