package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The heartbeat message intersection-service sends. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Heartbeat(String service, Long sequence, String timestamp) {
}
