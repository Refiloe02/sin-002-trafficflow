package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Folds rows that describe the same intersection into one record.
 *
 * <p>Strategy:
 * <ol>
 *   <li><b>Detect</b>: after cleaning, rows with the same ID are the same intersection ("INT-1005" and
 *       "int-1005" only differed by casing). Case differences are already gone, so an exact match is enough.</li>
 *   <li><b>District / signal type</b>: most common non-null value; a blank never overrides a known value.
 *       A tie goes to the value seen first.</li>
 *   <li><b>Active flag</b>: majority vote across non-null values. A tie fails safe to {@code false}
 *       (treating a possibly-broken signal as working is the riskier mistake). If nobody knows: {@code null}.</li>
 * </ol>
 */
public final class IntersectionDeduplicator {

    public record Result(List<IntersectionRecord> intersections, List<String> notes) {
    }

    private static final Comparator<IntersectionRecord> BY_ID =
            Comparator.comparingInt((IntersectionRecord r) -> numericId(r.id())).thenComparing(IntersectionRecord::id);

    public Result merge(List<IntersectionRecord> records) {
        Map<String, List<IntersectionRecord>> groups = new LinkedHashMap<>();
        for (IntersectionRecord record : records) {
            groups.computeIfAbsent(record.id(), id -> new ArrayList<>()).add(record);
        }
        List<IntersectionRecord> merged = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (List<IntersectionRecord> group : groups.values()) {
            merged.add(mergeGroup(group, notes));
        }
        merged.sort(BY_ID);
        return new Result(merged, notes);
    }

    private static IntersectionRecord mergeGroup(List<IntersectionRecord> group, List<String> notes) {
        String id = group.get(0).id();
        List<String> districts = group.stream().map(IntersectionRecord::district).filter(Objects::nonNull).toList();
        List<String> signals = group.stream().map(IntersectionRecord::signalType).filter(Objects::nonNull).toList();
        long yes = group.stream().filter(r -> Boolean.TRUE.equals(r.active())).count();
        long no = group.stream().filter(r -> Boolean.FALSE.equals(r.active())).count();
        Boolean active = (yes + no == 0) ? null : Boolean.valueOf(yes > no);

        if (group.size() > 1) {
            StringBuilder note = new StringBuilder(id).append(": merged ").append(group.size()).append(" rows");
            if (yes > 0 && no > 0) {
                note.append("; active flags disagreed (").append(yes).append(" true / ").append(no).append(" false) -> ")
                        .append(active).append(yes == no ? " (tie: failing safe)" : " (majority)");
            }
            if (districts.stream().distinct().count() > 1) {
                note.append("; districts disagreed -> ").append(mostCommon(districts));
            }
            if (signals.stream().distinct().count() > 1) {
                note.append("; signal types disagreed -> ").append(mostCommon(signals));
            }
            notes.add(note.toString());
        }
        return new IntersectionRecord(id, mostCommon(districts), mostCommon(signals), active);
    }

    /** Most frequent value; on a tie the value seen first wins. */
    private static String mostCommon(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static int numericId(String id) {
        try {
            return Integer.parseInt(id.substring(id.lastIndexOf('-') + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
