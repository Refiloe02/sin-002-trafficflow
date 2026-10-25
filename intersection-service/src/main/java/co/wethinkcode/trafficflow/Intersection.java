package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An intersection as served by ingestion-service. Unknown JSON fields are ignored so services evolve separately. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Intersection(String id, String district, String signalType, Boolean active) {
}
