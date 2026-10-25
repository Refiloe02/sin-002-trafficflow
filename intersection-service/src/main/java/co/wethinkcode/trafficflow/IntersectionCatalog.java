package co.wethinkcode.trafficflow;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Read-only, in-memory source of truth for which intersections and districts exist. */
public final class IntersectionCatalog {

    private final List<Intersection> intersections;

    public IntersectionCatalog(List<Intersection> intersections) {
        this.intersections = List.copyOf(intersections);
    }

    public List<Intersection> all() {
        return intersections;
    }

    /** Case-insensitive, ignores surrounding spaces. */
    public Optional<Intersection> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        return intersections.stream().filter(i -> i.id().equals(wanted)).findFirst();
    }

    /** Distinct, sorted, non-null district names. */
    public List<String> districts() {
        return intersections.stream().map(Intersection::district).filter(d -> d != null).distinct().sorted().toList();
    }

    public boolean isKnownDistrict(String name) {
        return name != null && districts().stream().anyMatch(d -> d.equalsIgnoreCase(name.trim()));
    }

    public List<Intersection> inDistrict(String district) {
        return intersections.stream()
                .filter(i -> i.district() != null && i.district().equalsIgnoreCase(district.trim()))
                .toList();
    }
}
