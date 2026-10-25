package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Stage 2: validates intersection / district names. It is the source of truth that routing-service asks
 * before estimating a route. Data comes from ingestion-service's cleaned output over REST (stage 2).
 * It also sends a heartbeat to intersection-heartbeat-queue so intersection-watchdog can tell when it dies (stage 4).
 */
public class IntersectionServiceApp {

    private static final String INGESTION_URL = System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7020");
    private static final IngestionClient INGESTION = new IngestionClient(INGESTION_URL);

    private static volatile IntersectionCatalog catalog;

    public static void main(String[] args) {
        long intervalSeconds = Long.parseLong(System.getenv().getOrDefault("HEARTBEAT_INTERVAL_SECONDS", "5"));
        // a beat nobody consumed within 3 intervals expires into the dead-letter queue
        ActiveMqHeartbeatPublisher heartbeatPublisher =
                new ActiveMqHeartbeatPublisher(MqConfig.BROKER_URL, intervalSeconds * 3 * 1000);
        HeartbeatSender heartbeats = new HeartbeatSender(heartbeatPublisher, Clock.systemUTC(), intervalSeconds * 1000);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            heartbeats.close();
            heartbeatPublisher.close();
        }));

        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /intersections[?district=Downtown]
        app.get("/intersections", ctx -> withCatalog(ctx, c -> {
            String district = ctx.queryParam("district");
            ctx.json(district == null ? c.all() : c.inDistrict(district));
        }));

        // GET /intersections/{id} -> 200 + record, or 404 if the id is not recognised
        app.get("/intersections/{id}", ctx -> withCatalog(ctx, c -> {
            String id = ctx.pathParam("id");
            c.find(id).ifPresentOrElse(
                    ctx::json,
                    () -> ctx.status(404).json(Map.of("error", "Unknown intersection: " + id)));
        }));

        // GET /districts -> ["Downtown", "Eastside", ...]
        app.get("/districts", ctx -> withCatalog(ctx, c -> ctx.json(c.districts())));

        // GET /districts/{name} -> 200 {"district": "...", "known": true} or 404 for an unrecognised district
        app.get("/districts/{name}", ctx -> withCatalog(ctx, c -> {
            String name = ctx.pathParam("name");
            if (c.isKnownDistrict(name)) {
                ctx.json(Map.of("district", name, "known", true));
            } else {
                ctx.status(404).json(Map.of("error", "Unknown district: " + name));
            }
        }));

        // GET /heartbeat -> how many heartbeats have been attempted and how often they are sent
        app.get("/heartbeat", ctx -> ctx.json(Map.of(
                "service", HeartbeatSender.SERVICE_NAME,
                "beatsAttempted", heartbeats.beatsAttempted(),
                "intervalMillis", heartbeats.intervalMillis())));

        // POST /refresh -> reload from ingestion-service
        app.post("/refresh", ctx -> {
            if (tryLoad()) {
                ctx.json(Map.of("intersections", catalog.all().size()));
            } else {
                ctx.status(503).json(Map.of("error", "ingestion-service unavailable at " + INGESTION_URL));
            }
        });

        tryLoad();   // best effort once every route exists; requests retry if ingestion was not ready yet
        heartbeats.start();
    }

    private static boolean tryLoad() {
        try {
            List<Intersection> loaded = INGESTION.fetchIntersections();
            catalog = new IntersectionCatalog(loaded);
            System.out.println("intersection-service loaded " + loaded.size() + " intersections from " + INGESTION_URL);
            return true;
        } catch (Exception e) {
            System.err.println("intersection-service could not load data from " + INGESTION_URL + ": " + e.getMessage());
            return false;
        }
    }

    private interface CatalogHandler {
        void handle(IntersectionCatalog catalog) throws Exception;
    }

    private static void withCatalog(Context ctx, CatalogHandler handler) throws Exception {
        if (catalog == null && !tryLoad()) {
            ctx.status(503).json(Map.of("error", "intersection data not available: ingestion-service unreachable"));
            return;
        }
        handler.handle(catalog);
    }
}
