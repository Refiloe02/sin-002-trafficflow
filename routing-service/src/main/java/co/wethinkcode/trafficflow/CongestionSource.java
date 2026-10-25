package co.wethinkcode.trafficflow;

import java.io.IOException;

/**
 * Where routing-service learns the current congestion level. Hiding it behind an interface is what lets stage 3
 * swap the per-request REST call for an MQ-fed cache without touching the estimate logic.
 */
public interface CongestionSource {

    int level() throws IOException;

    /** Shown in the API response so you can see where the number came from. */
    String name();
}
