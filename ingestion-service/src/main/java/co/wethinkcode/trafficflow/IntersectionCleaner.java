package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Turns raw rows from the messy {@code intersections-legacy.csv} into consistent {@link IntersectionRecord}s. */
public final class IntersectionCleaner {

    /** Values that mean "no data" in the legacy export (compared in lower case). */
    private static final Set<String> PLACEHOLDERS = Set.of("", "n/a", "na", "tbd", "unknown", "-", "nan", "null", "none");
    private static final Set<String> TRUE_FLAGS = Set.of("y", "yes", "true", "1", "t");
    private static final Set<String> FALSE_FLAGS = Set.of("n", "no", "false", "0", "f");

    /** Trim, collapse inner whitespace, and turn placeholders into {@code null}. */
    public static String tidy(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        return PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT)) ? null : value;
    }

    /** "int-1005 " becomes "INT-1005". */
    public static String normaliseId(String raw) {
        String value = tidy(raw);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** " downtown " becomes "Downtown". */
    public static String normaliseDistrict(String raw) {
        String value = tidy(raw);
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String word : value.split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** Signal types are lower-case labels: "4-Way" -> "4-way", "ROUNDABOUT" -> "roundabout". */
    public static String normaliseSignalType(String raw) {
        String value = tidy(raw);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Y / yes / TRUE / 1 -> true; N / no / FALSE / 0 -> false; anything else is unknown (null). */
    public static Boolean parseFlag(String raw) {
        String value = tidy(raw);
        if (value == null) {
            return null;
        }
        String key = value.toLowerCase(Locale.ROOT);
        if (TRUE_FLAGS.contains(key)) {
            return Boolean.TRUE;
        }
        if (FALSE_FLAGS.contains(key)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** First row is the header (padding in header names is ignored). Rows without an ID are skipped. */
    public List<IntersectionRecord> clean(List<String[]> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String[] header = rows.get(0);
        int id = column(header, "intersection_id");
        int district = column(header, "district");
        int signal = column(header, "signal_type");
        int active = column(header, "active_flag");

        List<IntersectionRecord> cleaned = new ArrayList<>();
        for (String[] row : rows.subList(1, rows.size())) {
            String intersectionId = normaliseId(cell(row, id));
            if (intersectionId == null) {
                continue;
            }
            cleaned.add(new IntersectionRecord(
                    intersectionId,
                    normaliseDistrict(cell(row, district)),
                    normaliseSignalType(cell(row, signal)),
                    parseFlag(cell(row, active))));
        }
        return cleaned;
    }

    private static int column(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null && header[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("CSV is missing required column: " + name);
    }

    private static String cell(String[] row, int index) {
        return index < row.length ? row[index] : null;
    }
}
