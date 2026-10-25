package co.wethinkcode.trafficflow;

import java.util.List;

/** Summary of what an ingestion run did - useful for the demo and for spotting bad data. */
public record CleaningReport(int rowsRead, int rowsSkipped, int duplicatesMerged, int intersectionsServed, List<String> notes) {
}
