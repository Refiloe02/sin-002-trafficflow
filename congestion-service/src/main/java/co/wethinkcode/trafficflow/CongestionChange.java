package co.wethinkcode.trafficflow;

/** The message published to congestion-topic, e.g. {"level":4,"timestamp":"2026-07-18T10:15:00Z"}. */
public record CongestionChange(int level, String timestamp) {
}
