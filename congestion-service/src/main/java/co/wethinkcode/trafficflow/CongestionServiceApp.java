package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.time.Clock;
import java.util.Map;

/**
 * Tracks the city-wide Congestion Level (0-8) behind a small REST API (stage 2) and publishes every real change
 * to the congestion-topic ActiveMQ topic (stage 3).
 */
public class CongestionServiceApp {

    public static void main(String[] args) {
        CongestionStore store = new CongestionStore();
        ActiveMqCongestionPublisher publisher = new ActiveMqCongestionPublisher();
        CongestionService service = new CongestionService(store, publisher, Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(new Thread(publisher::close));

        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /congestion -> {"level": 3}
        app.get("/congestion", ctx -> ctx.json(Map.of("level", store.get())));

        // POST /congestion {"level": 4} -> 200 {level, previousLevel, changed, published} | 400 for a bad body / out-of-range level
        app.post("/congestion", ctx -> {
            CongestionRequest request;
            try {
                request = ctx.bodyAsClass(CongestionRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Body must be JSON like {\"level\": 4}"));
                return;
            }
            if (request == null || request.level() == null) {
                ctx.status(400).json(Map.of("error", "Missing field: level"));
                return;
            }
            CongestionService.Update update;
            try {
                update = service.update(request.level());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
                return;
            }
            ctx.json(update);
        });
    }
}
