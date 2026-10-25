package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import io.javalin.Javalin;
import java.io.IOException;
import java.util.Map;

/**
 * Estimates travel time. Intersections are still validated over REST (stage 2); congestion now arrives by MQ
 * (stage 3) - routing-service subscribes to congestion-topic instead of polling congestion-service.
 */
public class RoutingServiceApp {

    public static void main(String[] args) {
        String intersectionUrl = System.getenv().getOrDefault("INTERSECTION_URL", "http://localhost:7021");

        CongestionCache congestionCache = new CongestionCache();
        CongestionSubscriber subscriber = new CongestionSubscriber(MqConfig.BROKER_URL, congestionCache);
        subscriber.start();
        Runtime.getRuntime().addShutdownHook(new Thread(subscriber::close));

        RoutingService routing = new RoutingService(new IntersectionClient(intersectionUrl), congestionCache, new RouteEstimator());

        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /route?from=INT-1001&to=INT-1005 -> 200 estimate | 400 bad input | 404 unknown | 409 inactive | 502 intersection-service down
        app.get("/route", ctx -> {
            String from = ctx.queryParam("from");
            String to = ctx.queryParam("to");
            if (from == null || to == null) {
                ctx.status(400).json(Map.of("error", "Both 'from' and 'to' query parameters are required"));
                return;
            }
            try {
                ctx.json(routing.route(from, to));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            } catch (RoutingService.UnknownIntersectionException e) {
                ctx.status(404).json(Map.of("error", e.getMessage()));
            } catch (RoutingService.InactiveIntersectionException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                ctx.status(502).json(Map.of("error", "Upstream service unavailable: " + e.getMessage()));
            }
        });

        // GET /congestion -> what the MQ subscription has taught us: {"level": 4, "source": "mq:congestion-topic"}
        app.get("/congestion", ctx -> ctx.json(Map.of("level", congestionCache.level(), "source", congestionCache.name())));
    }
}
