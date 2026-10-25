package co.wethinkcode.trafficflow;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The watchdog's brain: a small state machine (UNKNOWN -> UP -> DOWN -> UP ...) driven by heartbeats and the
 * passage of time. It never reads the clock itself - callers pass "now" - so every rule is testable instantly.
 *
 * <ul>
 *   <li>a heartbeat makes the state UP (and raises RECOVERED if it was DOWN)</li>
 *   <li>silence longer than {@code maxSilence} makes it DOWN and raises ONE HEARTBEAT_MISSED alert
 *       (no repeats while it stays down)</li>
 *   <li>if no heartbeat has ever been seen, silence is measured from when the watchdog started listening
 *       (not from when it was launched - while it cannot reach the broker it cannot know anything)</li>
 *   <li>a heartbeat found in the dead-letter queue raises HEARTBEAT_DEAD_LETTERED</li>
 * </ul>
 */
public final class HeartbeatMonitor {

    public enum State { UNKNOWN, UP, DOWN }

    public record Alert(String kind, String message, String timestamp) {
    }

    public record Status(String state, String lastHeartbeat, Long secondsSinceLastHeartbeat,
                         long maxSilenceSeconds, List<Alert> alerts) {
    }

    private static final int MAX_ALERTS = 100;

    private final Duration maxSilence;
    private Instant startedAt;
    private State state = State.UNKNOWN;
    private Instant lastSeen;
    private final List<Alert> alerts = new ArrayList<>();

    public HeartbeatMonitor(Duration maxSilence, Instant startedAt) {
        this.maxSilence = maxSilence;
        this.startedAt = startedAt;
    }

    public synchronized Optional<Alert> heartbeatReceived(Instant now) {
        State previous = state;
        state = State.UP;
        lastSeen = now;
        if (previous == State.DOWN) {
            return Optional.of(record("RECOVERED", "intersection-service is sending heartbeats again.", now));
        }
        return Optional.empty();
    }

    /** The listener (re)connected to the broker. Until a first heartbeat arrives, the silence clock restarts here. */
    public synchronized void listeningStarted(Instant now) {
        if (lastSeen == null && state != State.DOWN) {
            startedAt = now;
        }
    }

    /** Call periodically. Returns an alert only on the transition into DOWN. */
    public synchronized Optional<Alert> check(Instant now) {
        Instant reference = lastSeen != null ? lastSeen : startedAt;
        long silentSeconds = Duration.between(reference, now).getSeconds();
        if (state != State.DOWN && Duration.between(reference, now).compareTo(maxSilence) > 0) {
            state = State.DOWN;
            String message = lastSeen != null
                    ? "No heartbeat from intersection-service for " + silentSeconds + "s (limit " + maxSilence.getSeconds()
                            + "s). Routes can no longer be validated."
                    : "No heartbeat from intersection-service since the watchdog began listening " + silentSeconds
                            + "s ago. Routes can no longer be validated.";
            return Optional.of(record("HEARTBEAT_MISSED", message, now));
        }
        return Optional.empty();
    }

    public synchronized Alert deadLettered(Instant now, String detail) {
        return record("HEARTBEAT_DEAD_LETTERED",
                "A heartbeat expired unconsumed and landed in the dead-letter queue: " + detail, now);
    }

    public synchronized Status status(Instant now) {
        return new Status(
                state.name(),
                lastSeen == null ? null : lastSeen.truncatedTo(ChronoUnit.SECONDS).toString(),
                lastSeen == null ? null : Duration.between(lastSeen, now).getSeconds(),
                maxSilence.getSeconds(),
                List.copyOf(alerts));
    }

    private Alert record(String kind, String message, Instant now) {
        Alert alert = new Alert(kind, message, now.truncatedTo(ChronoUnit.SECONDS).toString());
        alerts.add(alert);
        if (alerts.size() > MAX_ALERTS) {
            alerts.remove(0);
        }
        return alert;
    }
}
