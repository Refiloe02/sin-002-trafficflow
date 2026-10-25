package co.wethinkcode.trafficflow;

import java.util.concurrent.atomic.AtomicInteger;

/** Holds the single city-wide Congestion Level (0 = free flowing ... 8 = gridlock). Thread-safe. */
public final class CongestionStore {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private final AtomicInteger level = new AtomicInteger(MIN_LEVEL);

    public int get() {
        return level.get();
    }

    /** Sets the level and returns the previous one. Rejects values outside 0-8 without changing state. */
    public int set(int newLevel) {
        if (newLevel < MIN_LEVEL || newLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + " but was " + newLevel);
        }
        return level.getAndSet(newLevel);
    }
}
