package co.wethinkcode.trafficflow;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Orchestrates one route request: validate both ends, read congestion, run the estimator. */
public final class RoutingService {

    public interface IntersectionSource {
        Optional<Intersection> find(String id) throws IOException;
    }

    public static class UnknownIntersectionException extends RuntimeException {
        public UnknownIntersectionException(String id) {
            super("Unknown intersection: " + id);
        }
    }

    public static class InactiveIntersectionException extends RuntimeException {
        public InactiveIntersectionException(String id) {
            super("Intersection " + id + " is inactive, so a route through it cannot be estimated");
        }
    }

    private final IntersectionSource intersections;
    private final CongestionSource congestion;
    private final RouteEstimator estimator;

    public RoutingService(IntersectionSource intersections, CongestionSource congestion, RouteEstimator estimator) {
        this.intersections = intersections;
        this.congestion = congestion;
        this.estimator = estimator;
    }

    /**
     * @throws IllegalArgumentException from == to (400)
     * @throws UnknownIntersectionException an endpoint is not recognised (404)
     * @throws InactiveIntersectionException an endpoint is switched off (409)
     * @throws IOException a dependency is down (502)
     */
    public Map<String, Object> route(String from, String to) throws IOException {
        if (from.trim().equalsIgnoreCase(to.trim())) {
            throw new IllegalArgumentException("from and to must be different intersections");
        }
        Intersection start = require(from);
        Intersection end = require(to);
        int level = congestion.level();
        RouteEstimator.Estimate estimate = estimator.estimate(start, end, level);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("baseMinutes", estimate.baseMinutes());
        breakdown.put("crossDistrictMinutes", estimate.crossDistrictMinutes());
        breakdown.put("signalMinutes", estimate.signalMinutes());
        breakdown.put("congestionMultiplier", estimate.congestionMultiplier());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", start.id());
        body.put("to", end.id());
        body.put("congestionLevel", level);
        body.put("congestionSource", congestion.name());
        body.put("estimatedMinutes", estimate.totalMinutes());
        body.put("breakdown", breakdown);
        return body;
    }

    private Intersection require(String id) throws IOException {
        Intersection found = intersections.find(id).orElseThrow(() -> new UnknownIntersectionException(id));
        if (Boolean.FALSE.equals(found.active())) {
            throw new InactiveIntersectionException(found.id());
        }
        return found;
    }
}
