package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs the real intersections-legacy.csv through the whole pipeline. */
class IngestionPipelineTest {

    private static IngestionResult result;

    @BeforeAll
    static void loadRealCsv() throws Exception {
        try (InputStream in = IngestionPipelineTest.class.getResourceAsStream("/intersections-legacy.csv")) {
            result = IngestionPipeline.run(IntersectionCsvReader.read(in));
        }
    }

    private IntersectionRecord get(String id) {
        return result.find(id).orElseThrow();
    }

    @Test
    void eighteenRawRowsBecomeSeventeenIntersections() {
        assertEquals(18, result.report().rowsRead());
        assertEquals(17, result.intersections().size());
        assertEquals(1, result.report().duplicatesMerged());
    }

    @Test
    void int1005IsMergedFromItsTwoCasings() {
        assertEquals(new IntersectionRecord("INT-1005", "Downtown", "roundabout", true), get("int-1005"));
        assertEquals(1, result.report().notes().size());
    }

    @Test
    void missingValuesBecomeExplicitNulls() {
        assertNull(get("INT-1007").signalType());    // blank signal type
        assertEquals(Boolean.TRUE, get("INT-1007").active());
        assertNull(get("INT-1015").district());      // blank district
        assertNull(get("INT-1004").signalType());    // "unknown"
        assertNull(get("INT-1013").active());        // "unknown" flag
    }

    @Test
    void spellingAndCasingVariantsAreNormalised() {
        assertEquals("4-way", get("INT-1006").signalType());       // "4-Way"
        assertEquals("pedestrian", get("INT-1014").signalType());  // "Pedestrian"
        assertEquals("Uptown", get("INT-1008").district());        // "uptown"
        assertEquals(Boolean.FALSE, get("INT-1017").active());     // "n"
    }

    @Test
    void outputIsSortedByNumericId() {
        List<String> ids = result.intersections().stream().map(IntersectionRecord::id).toList();
        assertEquals("INT-1001", ids.get(0));
        assertEquals("INT-1017", ids.get(ids.size() - 1));
        assertTrue(result.find("INT-9999").isEmpty());
    }
}
