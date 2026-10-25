package co.wethinkcode.trafficflow;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends a heartbeat every {@code intervalMillis}. A failed send (broker down) is logged and the next beat tries
 * again - the scheduler must never die just because the broker is away.
 */
public final class HeartbeatSender implements AutoCloseable {

    public static final String SERVICE_NAME = "intersection-service";

    private final HeartbeatPublisher publisher;
    private final Clock clock;
    private final long intervalMillis;
    private final AtomicLong sequence = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heartbeat-sender");
        thread.setDaemon(true);
        return thread;
    });

    public HeartbeatSender(HeartbeatPublisher publisher, Clock clock, long intervalMillis) {
        this.publisher = publisher;
        this.clock = clock;
        this.intervalMillis = intervalMillis;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::beat, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    void beat() {
        Heartbeat heartbeat = new Heartbeat(SERVICE_NAME, sequence.incrementAndGet(),
                clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
        try {
            publisher.publish(heartbeat);
        } catch (Exception e) {
            System.err.println("intersection-service could not send heartbeat #" + heartbeat.sequence() + ": " + e.getMessage());
        }
    }

    public long beatsAttempted() {
        return sequence.get();
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
