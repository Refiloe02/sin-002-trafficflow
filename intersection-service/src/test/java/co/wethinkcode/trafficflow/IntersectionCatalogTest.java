package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IntersectionCatalogTest {

    private final IntersectionCatalog catalog = new IntersectionCatalog(List.of(
            new Intersection("INT-1001", "Downtown", "4-way", true),
            new Intersection("INT-1002", "Midtown", "pedestrian", true),
            new Intersection("INT-1003", "Downtown", "4-way", false),
            new Intersection("INT-1015", null, "4-way", true)));

    @Test
    void findIgnoresCaseAndPadding() {
        assertEquals("INT-1002", catalog.find(" int-1002 ").orElseThrow().id());
    }

    @Test
    void unknownOrNullIdIsEmpty() {
        assertTrue(catalog.find("INT-9999").isEmpty());
        assertTrue(catalog.find(null).isEmpty());
    }

    @Test
    void districtsAreDistinctSortedAndSkipMissingOnes() {
        assertEquals(List.of("Downtown", "Midtown"), catalog.districts());
    }

    @Test
    void districtValidationIgnoresCase() {
        assertTrue(catalog.isKnownDistrict("downtown"));
        assertFalse(catalog.isKnownDistrict("Atlantis"));
        assertFalse(catalog.isKnownDistrict(null));
    }

    @Test
    void inDistrictFilters() {
        assertEquals(2, catalog.inDistrict("DOWNTOWN").size());
        assertEquals(0, catalog.inDistrict("Westside").size());
    }
}
