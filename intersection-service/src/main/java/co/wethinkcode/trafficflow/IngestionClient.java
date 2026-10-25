package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Synchronous REST call: GET {ingestion}/intersections -> JSON array of cleaned intersections. */
public final class IngestionClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public IngestionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<Intersection> fetchIntersections() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/intersections"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ingestion-service answered HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), new TypeReference<List<Intersection>>() {
        });
    }
}
