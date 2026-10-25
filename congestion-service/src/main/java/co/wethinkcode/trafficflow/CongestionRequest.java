package co.wethinkcode.trafficflow;

/** Body of POST /congestion, e.g. {"level": 4}. Integer (not int) so a missing field is detectable. */
public record CongestionRequest(Integer level) {
}
