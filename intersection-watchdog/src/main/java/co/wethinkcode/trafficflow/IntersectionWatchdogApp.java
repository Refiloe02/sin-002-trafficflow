package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import io.javalin.Javalin;
import java.time.Clock;
import java.time.Duration;

/**
 * Stage 4: cries for help if intersection-service crashes (routes can no longer be validated). It does not poll
 * /health; it listens for heartbeats on intersection-heartbeat-queue and raises an alert when they stop, or when
 * one lands in the dead-letter queue.
 *
 * <p>HEARTBEAT_INTERVAL_SECONDS (default 5) must match intersection-service; the watchdog tolerates
 * MAX_MISSED_BEATS (default 3) missed beats before it declares the service DOWN.
 */
public class IntersectionWatchdogApp {

    public static void main(String[] args) {
        long intervalSeconds = Long.parseLong(System.getenv().getOrDefault("HEARTBEAT_INTERVAL_SECONDS", "5"));
        long missedBeats = Long.parseLong(System.getenv().getOrDefault("MAX_MISSED_BEATS", "3"));

        WatchdogService watchdog = new WatchdogService(Duration.ofSeconds(intervalSeconds * missedBeats), Clock.systemUTC());
        HeartbeatListener listener = new HeartbeatListener(MqConfig.BROKER_URL, watchdog, 5000);
        watchdog.start();
        listener.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            listener.close();
            watchdog.close();
        }));

        Javalin app = Javalin.create().start(7024);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /alert -> {"state":"UP|DOWN|UNKNOWN","lastHeartbeat":...,"secondsSinceLastHeartbeat":...,"alerts":[...]}
        app.get("/alert", ctx -> ctx.json(watchdog.status()));
    }
}
