package app.meethalfway.locations.adapters.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.config.MeetHalfwayProperties.Geocoding;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.locations.domain.port.AddressSuggestion;
import app.meethalfway.locations.domain.port.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeocodingAdapterTest {

    private static final String SEARCH_RESULTS = """
            [{"display_name":"Universidad EAFIT, Medellín","place_id":456,
              "osm_type":"way","osm_id":"123","lat":"6.2000","lon":"-75.5780"}]
            """;
    private static final String LOOKUP_RESULTS = """
            [{"display_name":"Universidad EAFIT, Medellín","osm_type":"way",
              "osm_id":"123","lat":"6.2000","lon":"-75.5780"}]
            """;

    private final List<URI> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private GeocodingAdapter adapter;

    @BeforeEach
    void startFakeProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI());
            String body = exchange.getRequestURI().getPath().equals("/lookup")
                    ? LOOKUP_RESULTS
                    : SEARCH_RESULTS;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        adapter = new GeocodingAdapter(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                new Geocoding("http://127.0.0.1:" + server.getAddress().getPort(), "", "MeetHalfwayTest/1.0", 2000L, 5));
    }

    @AfterEach
    void stopFakeProvider() {
        server.stop(0);
    }

    @Test
    void autocompleteUsesSearchEndpointAndReturnsResolvableOsmReference() {
        List<AddressSuggestion> suggestions = adapter.autocomplete("Universidad EAFIT", config());

        assertThat(suggestions).containsExactly(
                new AddressSuggestion("Universidad EAFIT, Medellín", "W123"));
        assertThat(requests.get(0).getPath()).isEqualTo("/search");
        assertThat(requests.get(0).getRawQuery())
                .contains("format=json", "q=Universidad+EAFIT", "limit=5", "viewbox=");
    }

    @Test
    void selectedOsmReferenceUsesLookupInsteadOfFuzzyAddressSearch() {
        GeocodeResult result = adapter.resolve("W123");

        assertThat(result).isEqualTo(GeocodeResult.resolved(new Coordinate(6.2, -75.578)));
        assertThat(requests.get(0).getPath()).isEqualTo("/lookup");
        assertThat(requests.get(0).getRawQuery()).contains("format=json", "osm_ids=W123");
    }

    @Test
    void freeFormAddressStillUsesSearchEndpoint() {
        GeocodeResult result = adapter.resolve("Universidad EAFIT, Medellín");

        assertThat(result).isEqualTo(GeocodeResult.resolved(new Coordinate(6.2, -75.578)));
        assertThat(requests.get(0).getPath()).isEqualTo("/search");
        assertThat(requests.get(0).getRawQuery()).contains("limit=1", "q=Universidad+EAFIT");
    }

    private static EngineConfig config() {
        return new EngineConfig(
                new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
                8,
                20000.0,
                new OutlierRule(2.0),
                0.5);
    }
}
