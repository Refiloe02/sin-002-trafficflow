package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HeartbeatMonitorTest {

    private static final Instant T0 = Instant.parse("2026-07-18T10:00:00Z");
    private final HeartbeatMonitor monitor = new HeartbeatMonitor(Duration.ofSeconds(15), T0);

    @Test
    void startsUnknownAndQuiet() {
        assertEquals("UNKNOWN", monitor.status(T0).state());
        assertTrue(monitor.check(T0.plusSeconds(10)).isEmpty());   // inside the grace period
    }

    @Test
    void aHeartbeatMakesTheServiceUp() {
        monitor.heartbeatReceived(T0.plusSeconds(2));
        assertEquals("UP", monitor.status(T0.plusSeconds(3)).state());
        assertEquals(1L, monitor.status(T0.plusSeconds(3)).secondsSinceLastHeartbeat());
    }

    @Test
    void silenceBeyondTheLimitRaisesExactlyOneAlert() {
        monitor.heartbeatReceived(T0);
        assertTrue(monitor.check(T0.plusSeconds(15)).isEmpty());                  // exactly at the limit is still fine
        HeartbeatMonitor.Alert alert = monitor.check(T0.plusSeconds(16)).orElseThrow();
        assertEquals("HEARTBEAT_MISSED", alert.kind());
        assertEquals("DOWN", monitor.status(T0.plusSeconds(16)).state());
        assertTrue(monitor.check(T0.plusSeconds(30)).isEmpty());                  // no repeat while it stays down
    }

    @Test
    void neverHearingFromTheServiceIsAlsoAnAlert() {
        HeartbeatMonitor.Alert alert = monitor.check(T0.plusSeconds(20)).orElseThrow();
        assertEquals("HEARTBEAT_MISSED", alert.kind());
        assertTrue(alert.message().contains("since the watchdog began listening"));
        assertNull(monitor.status(T0.plusSeconds(20)).lastHeartbeat());
    }

    @Test
    void silenceIsCountedFromWhenTheWatchdogStartedListeningNotFromLaunch() {
        monitor.listeningStarted(T0.plusSeconds(30));                              // broker only became reachable at +30s
        assertTrue(monitor.check(T0.plusSeconds(40)).isEmpty());                   // 10s of listening: still within the limit
        assertEquals("HEARTBEAT_MISSED", monitor.check(T0.plusSeconds(46)).orElseThrow().kind());
    }

    @Test
    void reconnectingDoesNotResetTheClockOnceHeartbeatsHaveBeenSeen() {
        monitor.heartbeatReceived(T0);
        monitor.listeningStarted(T0.plusSeconds(14));                              // reconnect after a broker blip
        assertEquals("HEARTBEAT_MISSED", monitor.check(T0.plusSeconds(16)).orElseThrow().kind());
    }

    @Test
    void aHeartbeatAfterDowntimeRaisesRecovered() {
        monitor.heartbeatReceived(T0);
        monitor.check(T0.plusSeconds(20));                                         // goes DOWN
        HeartbeatMonitor.Alert recovered = monitor.heartbeatReceived(T0.plusSeconds(25)).orElseThrow();
        assertEquals("RECOVERED", recovered.kind());
        assertEquals("UP", monitor.status(T0.plusSeconds(25)).state());
    }

    @Test
    void deadLetteredHeartbeatIsRecordedAsAnAlert() {
        HeartbeatMonitor.Alert alert = monitor.deadLettered(T0.plusSeconds(1), "sequence 9");
        assertEquals("HEARTBEAT_DEAD_LETTERED", alert.kind());
        assertEquals(1, monitor.status(T0.plusSeconds(1)).alerts().size());
    }
}
