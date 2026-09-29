package app.meethalfway.routing.adapters.routing;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.config.MeetHalfwayProperties.Routing;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.TransportMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OsrmRoutingAdapterTest {

    private final List<URI> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private OsrmRoutingAdapter adapter;
    private String responseBody;
    private boolean dynamicResponse;

    @BeforeEach
    void startFakeProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI());
            String body = responseBody;
            if (dynamicResponse) {
                int destinationCount = queryParameter(exchange.getRequestURI(), "destinations")
                        .split(";").length;
                String durations = String.join(",", Collections.nCopies(destinationCount, "60"));
                body = "{\"code\":\"Ok\",\"durations\":[[" + durations + "]]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        adapter = new OsrmRoutingAdapter(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                new Routing("http://127.0.0.1:" + server.getAddress().getPort(),
                        "driving", "walking", 2000L, ""));
    }

    @AfterEach
    void stopFakeProvider() {
        server.stop(0);
    }

    @Test
    void tableApiReturnsRoundedTimesAndKeepsUnroutableCellsExplicit() {
        responseBody = """
                {"code":"Ok","durations":[[119.0,120.0],[null,420.0]]}
                """;
        List<Coordinate> origins = List.of(
                new Coordinate(6.20, -75.60), new Coordinate(6.30, -75.50));
        List<Coordinate> destinations = List.of(
                new Coordinate(6.25, -75.55), new Coordinate(6.28, -75.52));

        List<List<RouteResult>> matrix = adapter.travelTimes(
                origins, destinations, TransportMode.DRIVING);

        assertThat(matrix).hasSize(2).allSatisfy(row -> assertThat(row).hasSize(2));
        assertThat(((RouteResult.Success) matrix.get(0).get(0)).travelTime().value()).isEqualTo(2);
        assertThat(((RouteResult.Success) matrix.get(0).get(1)).travelTime().value()).isEqualTo(2);
        assertThat(matrix.get(1).get(0)).isInstanceOf(RouteResult.Failure.class);
        assertThat(((RouteResult.Failure) matrix.get(1).get(0)).reason())
                .contains("No route could be computed");
        assertThat(((RouteResult.Success) matrix.get(1).get(1)).travelTime().value()).isEqualTo(7);

        URI request = requests.get(0);
        assertThat(request.getPath()).startsWith("/table/v1/driving/");
        assertThat(request.getRawQuery())
                .contains("annotations=duration", "sources=0;1", "destinations=2;3");
    }

    @Test
    void splitsLargeCandidateGridWithoutExceedingOsrmCoordinateLimit() {
        dynamicResponse = true;
        List<Coordinate> destinations = Collections.nCopies(101, new Coordinate(6.25, -75.55));

        List<List<RouteResult>> matrix = adapter.travelTimes(
                List.of(new Coordinate(6.20, -75.60)), destinations, TransportMode.WALKING);

        assertThat(matrix).hasSize(1);
        assertThat(matrix.get(0)).hasSize(101);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getPath()).startsWith("/table/v1/walking/");
        assertThat(requests.get(0).getRawQuery())
                .contains("sources=0", "destinations=1;");
        assertThat(queryParameter(requests.get(0), "destinations").split(";")).hasSize(99);
        assertThat(queryParameter(requests.get(1), "destinations")).isEqualTo("1;2");
        assertThat(matrix.get(0)).allSatisfy(result -> assertThat(result).isInstanceOf(RouteResult.Success.class));
    }

    private static String queryParameter(URI uri, String name) {
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .filter(parameter -> parameter.startsWith(name + "="))
                .map(parameter -> parameter.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
