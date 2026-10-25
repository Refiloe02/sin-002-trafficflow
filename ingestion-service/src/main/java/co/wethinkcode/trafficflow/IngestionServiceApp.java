package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.io.InputStream;
import java.util.Map;

/** Stage 1: cleans intersections-legacy.csv once at start-up and serves the result to other services. */
public class IngestionServiceApp {

    public static void main(String[] args) throws Exception {
        IngestionResult result = load();

        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /intersections -> [{"id":"INT-1001","district":"Downtown","signalType":"4-way","active":true}, ...]
        app.get("/intersections", ctx -> ctx.json(result.intersections()));

        // GET /intersections/{id} -> 200 + record, 404 if unknown
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            result.find(id).ifPresentOrElse(
                    ctx::json,
                    () -> ctx.status(404).json(Map.of("error", "Unknown intersection: " + id)));
        });

        // GET /report -> what the cleaning run did
        app.get("/report", ctx -> ctx.json(result.report()));
    }

    static IngestionResult load() throws Exception {
        try (InputStream in = IngestionServiceApp.class.getResourceAsStream("/intersections-legacy.csv")) {
            if (in == null) {
                throw new IllegalStateException("intersections-legacy.csv not found on the classpath");
            }
            return IngestionPipeline.run(IntersectionCsvReader.read(in));
        }
    }
}
