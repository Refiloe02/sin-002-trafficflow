package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads raw CSV rows (header included) with OpenCSV. No cleaning happens here. */
public final class IntersectionCsvReader {

    private IntersectionCsvReader() {
    }

    public static List<String[]> read(InputStream in) throws IOException, CsvException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.readAll();
        }
    }
}
