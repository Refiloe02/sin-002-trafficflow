package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RouteEstimatorTest {

    private final RouteEstimator estimator = new RouteEstimator();
    private final Intersection fourWayDowntown = new Intersection("INT-1001", "Downtown", "4-way", true);
    private final Intersection roundaboutDowntown = new Intersection("INT-1005", "Downtown", "roundabout", true);
    private final Intersection pedestrianMidtown = new Intersection("INT-1002", "Midtown", "pedestrian", true);
    private final Intersection unknownEverything = new Intersection("INT-1013", null, null, null);

    @Test
    void sameDistrictFreeFlow() {
        RouteEstimator.Estimate e = estimator.estimate(fourWayDowntown, roundaboutDowntown, 0);
        assertEquals(0, e.crossDistrictMinutes());
        assertEquals(1.5, e.signalMinutes());          // 1 (4-way) + 0.5 (roundabout)
        assertEquals(14, e.totalMinutes());            // 13.5 rounds up
    }

    @Test
    void crossingDistrictsAddsSixMinutes() {
        RouteEstimator.Estimate e = estimator.estimate(fourWayDowntown, pedestrianMidtown, 0);
        assertEquals(6, e.crossDistrictMinutes());
        assertEquals(21, e.totalMinutes());            // 12 + 6 + 1 + 2
    }

    @Test
    void eachCongestionLevelAddsAQuarterOfTheFreeFlowTime() {
        assertEquals(27, estimator.estimate(fourWayDowntown, roundaboutDowntown, 4).totalMinutes());   // 13.5 x 2.0
        assertEquals(41, estimator.estimate(fourWayDowntown, roundaboutDowntown, 8).totalMinutes());   // 13.5 x 3.0 = 40.5
    }

    @Test
    void missingDataFallsBackToDefaultsInsteadOfFailing() {
        RouteEstimator.Estimate e = estimator.estimate(unknownEverything, fourWayDowntown, 0);
        assertEquals(0, e.crossDistrictMinutes());     // unknown district: do not guess a crossing
        assertEquals(2.0, e.signalMinutes());          // 1 (unknown) + 1 (4-way)
    }

    @Test
    void invalidCongestionLevelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(fourWayDowntown, roundaboutDowntown, 9));
    }
}
