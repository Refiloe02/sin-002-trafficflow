package co.wethinkcode.trafficflow;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The latest congestion level, fed by MQ messages. Replaces the per-request REST call to congestion-service:
 * routing-service answers from its own memory and keeps working even when congestion-service is down.
 *
 * <p>Trade-off: until the first message arrives we assume level 0 (free flowing).
 */
public final class CongestionCache implements CongestionSource {

    private final AtomicInteger level = new AtomicInteger(0);

    public void update(int newLevel) {
        level.set(newLevel);
    }

    @Override
    public int level() {
        return level.get();
    }

    @Override
    public String name() {
        return "mq:congestion-topic";
    }
}
