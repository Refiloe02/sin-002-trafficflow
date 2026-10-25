package co.wethinkcode.trafficflow;

/** The "I am alive" message, e.g. {"service":"intersection-service","sequence":42,"timestamp":"2026-07-18T10:15:00Z"}. */
public record Heartbeat(String service, long sequence, String timestamp) {
}
