package co.wethinkcode.trafficflow;

import java.util.List;

/** clean -> de-duplicate -> report. Separate from the web layer so it is easy to test. */
public final class IngestionPipeline {

    private IngestionPipeline() {
    }

    public static IngestionResult run(List<String[]> rows) {
        List<IntersectionRecord> cleaned = new IntersectionCleaner().clean(rows);
        IntersectionDeduplicator.Result merged = new IntersectionDeduplicator().merge(cleaned);

        int rowsRead = Math.max(0, rows.size() - 1);                    // minus the header
        CleaningReport report = new CleaningReport(
                rowsRead,
                rowsRead - cleaned.size(),
                cleaned.size() - merged.intersections().size(),
                merged.intersections().size(),
                merged.notes());
        return new IngestionResult(merged.intersections(), report);
    }
}
