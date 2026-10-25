package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoutingServiceTest {

    private static final Map<String, Intersection> DATA = Map.of(
            "INT-1001", new Intersection("INT-1001", "Downtown", "4-way", true),
            "INT-1005", new Intersection("INT-1005", "Downtown", "roundabout", true),
            "INT-1003", new Intersection("INT-1003", "Downtown", "4-way", false));

    private RoutingService service(int level) {
        RoutingService.IntersectionSource source = id -> Optional.ofNullable(DATA.get(id.trim().toUpperCase()));
        CongestionSource congestion = new CongestionSource() {
            public int level() {
                return level;
            }

            public String name() {
                return "fake";
            }
        };
        return new RoutingService(source, congestion, new RouteEstimator());
    }

    @Test
    void validRouteReturnsEstimateAndBreakdown() throws IOException {
        Map<String, Object> route = service(4).route("int-1001", "INT-1005");
        assertEquals("INT-1001", route.get("from"));
        assertEquals("INT-1005", route.get("to"));
        assertEquals(4, route.get("congestionLevel"));
        assertEquals("fake", route.get("congestionSource"));
        assertEquals(27L, route.get("estimatedMinutes"));
    }

    @Test
    void unknownIntersectionIsReportedByName() {
        RoutingService.UnknownIntersectionException e = assertThrows(
                RoutingService.UnknownIntersectionException.class, () -> service(0).route("INT-1001", "INT-9999"));
        assertEquals("Unknown intersection: INT-9999", e.getMessage());
    }

    @Test
    void inactiveIntersectionIsRefused() {
        assertThrows(RoutingService.InactiveIntersectionException.class, () -> service(0).route("INT-1001", "INT-1003"));
    }

    @Test
    void routeToTheSameIntersectionIsABadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service(0).route("INT-1001", " int-1001 "));
    }

    @Test
    void intersectionServiceOutageSurfacesAsIOException() {
        RoutingService.IntersectionSource down = id -> {
            throw new IOException("intersection-service down");
        };
        RoutingService svc = new RoutingService(down, new CongestionCache(), new RouteEstimator());
        assertThrows(IOException.class, () -> svc.route("INT-1001", "INT-1005"));
    }
}
