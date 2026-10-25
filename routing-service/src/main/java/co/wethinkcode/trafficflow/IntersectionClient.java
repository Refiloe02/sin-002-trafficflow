package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** REST call: GET {intersection-service}/intersections/{id}. 404 means "not recognised". */
public final class IntersectionClient implements RoutingService.IntersectionSource {

    private final RestClient rest = new RestClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public IntersectionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<Intersection> find(String id) throws IOException {
        RestClient.Reply reply = rest.get(baseUrl + "/intersections/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
        if (reply.status() == 404) {
            return Optional.empty();
        }
        if (reply.status() != 200) {
            throw new IOException("intersection-service answered HTTP " + reply.status());
        }
        return Optional.of(mapper.readValue(reply.body(), Intersection.class));
    }
}
