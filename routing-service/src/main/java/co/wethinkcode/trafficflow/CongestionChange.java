package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The message congestion-service publishes on congestion-topic. Integer so a missing field is detectable. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CongestionChange(Integer level, String timestamp) {
}
