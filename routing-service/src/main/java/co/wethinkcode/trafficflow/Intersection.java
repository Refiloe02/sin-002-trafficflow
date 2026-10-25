package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The slice of an intersection routing-service needs, as returned by intersection-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Intersection(String id, String district, String signalType, Boolean active) {
}
