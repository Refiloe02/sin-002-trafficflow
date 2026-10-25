package co.wethinkcode.trafficflow;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Connects the monitor to the clock: feeds it heartbeats, checks for silence every second, and "cries for help". */
public final class WatchdogService implements AutoCloseable {

    private final HeartbeatMonitor monitor;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "watchdog-check");
        thread.setDaemon(true);
        return thread;
    });

    public WatchdogService(Duration maxSilence, Clock clock) {
        this.clock = clock;
        this.monitor = new HeartbeatMonitor(maxSilence, clock.instant());
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkNow, 1, 1, TimeUnit.SECONDS);
    }

    void checkNow() {
        monitor.check(clock.instant()).ifPresent(a -> System.err.println("[ALERT] " + a.kind() + ": " + a.message()));
    }

    /** Called by the listener each time it (re)connects to the broker. */
    public void onBrokerConnected() {
        monitor.listeningStarted(clock.instant());
    }

    public void onHeartbeat() {
        monitor.heartbeatReceived(clock.instant()).ifPresent(a -> System.out.println("[RECOVERED] " + a.message()));
    }

    public void onDeadLettered(String detail) {
        HeartbeatMonitor.Alert alert = monitor.deadLettered(clock.instant(), detail);
        System.err.println("[ALERT] " + alert.kind() + ": " + alert.message());
    }

    public HeartbeatMonitor.Status status() {
        return monitor.status(clock.instant());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
