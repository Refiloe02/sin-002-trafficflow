package co.wethinkcode.trafficflow;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The cleaned, de-duplicated intersections plus the report describing how they were produced. */
public record IngestionResult(List<IntersectionRecord> intersections, CleaningReport report) {

    public Optional<IntersectionRecord> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        return intersections.stream().filter(i -> i.id().equals(wanted)).findFirst();
    }
}
