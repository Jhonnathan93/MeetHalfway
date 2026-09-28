package app.meethalfway.locations.adapters.geocoding;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Minimal seam over the raw HTTP call the {@link GeocodingAdapter} performs.
 *
 * <p>Extracting the single "send this request, give me the response body" step
 * behind an interface keeps the adapter's mapping logic (query → suggestions,
 * response → coordinate) unit-testable <em>without touching the network</em>:
 * tests inject a stub that returns canned provider JSON. Production wiring uses
 * {@link JdkHttpExchange}, which is backed by the JDK's built-in
 * {@link java.net.http.HttpClient} so no new dependency is introduced.
 */
@FunctionalInterface
public interface HttpExchange {

    /**
     * Sends the request and returns the response body as a UTF-8 string.
     *
     * @param request the fully-built HTTP request (URL, headers, timeout)
     * @return the response body
     * @throws IOException          when the exchange fails at the transport level
     * @throws InterruptedException when the calling thread is interrupted
     */
    String send(HttpRequest request) throws IOException, InterruptedException;
}
