package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CongestionCacheTest {

    private final CongestionCache cache = new CongestionCache();

    @Test
    void startsFreeFlowingUntilTheFirstMessage() {
        assertEquals(0, cache.level());
    }

    @Test
    void latestUpdateWins() {
        cache.update(3);
        cache.update(7);
        assertEquals(7, cache.level());
    }

    @Test
    void reportsItsSource() {
        assertEquals("mq:congestion-topic", cache.name());
    }
}
