package co.wethinkcode.trafficflow;

/**
 * One cleaned intersection. {@code district}, {@code signalType} and {@code active} are {@code null} when the
 * legacy file did not say - "unknown" is deliberately different from {@code false} or an empty string.
 */
public record IntersectionRecord(String id, String district, String signalType, Boolean active) {
}
