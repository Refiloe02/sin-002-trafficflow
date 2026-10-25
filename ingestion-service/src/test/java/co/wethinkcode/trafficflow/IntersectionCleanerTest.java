package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class IntersectionCleanerTest {

    @Test
    void tidyTrimsAndTurnsPlaceholdersIntoNull() {
        assertEquals("Downtown", IntersectionCleaner.tidy("  Downtown "));
        for (String placeholder : new String[] {"", "  ", "N/A", "TBD", "unknown", "-", "NaN"}) {
            assertNull(IntersectionCleaner.tidy(placeholder), placeholder);
        }
        assertNull(IntersectionCleaner.tidy(null));
    }

    @Test
    void idsAreTrimmedAndUpperCased() {
        assertEquals("INT-1003", IntersectionCleaner.normaliseId("INT-1003 "));
        assertEquals("INT-1002", IntersectionCleaner.normaliseId("int-1002"));
    }

    @Test
    void districtsAreTitleCased() {
        assertEquals("Downtown", IntersectionCleaner.normaliseDistrict(" downtown "));
        assertEquals("Eastside", IntersectionCleaner.normaliseDistrict("EASTSIDE"));
        assertNull(IntersectionCleaner.normaliseDistrict(""));
    }

    @Test
    void signalTypesAreLowerCaseLabels() {
        assertEquals("4-way", IntersectionCleaner.normaliseSignalType("4-Way"));
        assertEquals("roundabout", IntersectionCleaner.normaliseSignalType("ROUNDABOUT"));
        assertNull(IntersectionCleaner.normaliseSignalType("unknown"));
    }

    @Test
    void flagsBecomeBooleansOrNull() {
        for (String yes : new String[] {"Y", "yes", "true", "TRUE", "1", "YES"}) {
            assertEquals(Boolean.TRUE, IntersectionCleaner.parseFlag(yes), yes);
        }
        for (String no : new String[] {"N", "n", "no", "FALSE", "0"}) {
            assertEquals(Boolean.FALSE, IntersectionCleaner.parseFlag(no), no);
        }
        assertNull(IntersectionCleaner.parseFlag("unknown"));
    }

    @Test
    void readmeWorkedExampleProducesTheDocumentedShape() {
        List<String[]> rows = List.of(
                new String[] {"intersection_id", "District ", "signal_type", "active_flag"},
                new String[] {"INT-1001", " Downtown ", "4-way", "Y"},
                new String[] {"INT-1007", "Eastside", "", "1"},
                new String[] {"INT-1015", "", "4-way", "Y"});

        List<IntersectionRecord> cleaned = new IntersectionCleaner().clean(rows);

        assertEquals(new IntersectionRecord("INT-1001", "Downtown", "4-way", true), cleaned.get(0));
        assertEquals(new IntersectionRecord("INT-1007", "Eastside", null, true), cleaned.get(1));   // blank kept as explicit null
        assertEquals(new IntersectionRecord("INT-1015", null, "4-way", true), cleaned.get(2));
    }

    @Test
    void rowsWithoutAnIdAreSkipped() {
        List<String[]> rows = List.of(
                new String[] {"intersection_id", "district", "signal_type", "active_flag"},
                new String[] {"", "Downtown", "4-way", "Y"});
        assertEquals(0, new IntersectionCleaner().clean(rows).size());
    }

    @Test
    void missingColumnIsReported() {
        List<String[]> rows = List.<String[]>of(new String[] {"intersection_id"});
        assertThrows(IllegalArgumentException.class, () -> new IntersectionCleaner().clean(rows));
    }
}
