package co.wethinkcode.trafficflow;

import java.util.Map;

/**
 * Pure maths for the estimated travel time - no I/O, easy to test.
 *
 * <pre>
 * free-flow minutes = 12 (base) + 6 (if the two districts differ) + signal delay at both ends
 * estimate          = round(free-flow minutes x (1 + 0.25 x congestionLevel))
 * </pre>
 * Signal delay per intersection: roundabout 0.5, 4-way 1, stop-sign 1.5, pedestrian 2, unknown 1 minute.
 */
public final class RouteEstimator {

    static final double BASE_MINUTES = 12;
    static final double CROSS_DISTRICT_MINUTES = 6;
    static final double CONGESTION_STEP = 0.25;

    private static final Map<String, Double> SIGNAL_DELAY = Map.of(
            "roundabout", 0.5,
            "4-way", 1.0,
            "stop-sign", 1.5,
            "pedestrian", 2.0);
    private static final double UNKNOWN_SIGNAL_DELAY = 1.0;

    public record Estimate(double baseMinutes, double crossDistrictMinutes, double signalMinutes,
                           double congestionMultiplier, long totalMinutes) {
    }

    public Estimate estimate(Intersection from, Intersection to, int congestionLevel) {
        if (congestionLevel < 0 || congestionLevel > 8) {
            throw new IllegalArgumentException("congestion level must be 0-8 but was " + congestionLevel);
        }
        boolean crossesDistricts = from.district() != null && to.district() != null
                && !from.district().equalsIgnoreCase(to.district());
        double cross = crossesDistricts ? CROSS_DISTRICT_MINUTES : 0;
        double signals = signalDelay(from) + signalDelay(to);
        double multiplier = 1 + CONGESTION_STEP * congestionLevel;
        long total = Math.round((BASE_MINUTES + cross + signals) * multiplier);
        return new Estimate(BASE_MINUTES, cross, signals, multiplier, total);
    }

    private static double signalDelay(Intersection i) {
        return i.signalType() == null ? UNKNOWN_SIGNAL_DELAY : SIGNAL_DELAY.getOrDefault(i.signalType(), UNKNOWN_SIGNAL_DELAY);
    }
}
