package app.meethalfway.adapters.geocoding;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Production {@link HttpExchange} backed by the JDK's built-in
 * {@link HttpClient} (no third-party HTTP dependency, per task 10.2).
 *
 * <p>It only performs the transport step: send the request, verify a 2xx
 * status, and return the body. All URL building, header setting (including the
 * policy-mandated {@code User-Agent}), timeouts, and JSON mapping live in
 * {@link GeocodingAdapter}. Non-2xx responses become {@link IOException} so the
 * adapter can translate them into a domain-level "not found" reason without any
 * provider details or credentials leaking.
 */
public final class JdkHttpExchange implements HttpExchange {

    private final HttpClient client;

    /**
     * Creates an exchange over the given client.
     *
     * @param client the JDK HTTP client to use; must not be null
     */
    public JdkHttpExchange(HttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
    }

    @Override
    public String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // Do not include the URL (it may carry a key/query); status only.
            throw new IOException("geocoding provider returned HTTP status " + status);
        }
        return response.body();
    }
}
