package app.meethalfway.adapters.web;

import app.meethalfway.adapters.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hand-rolled, dependency-free rate limiter (Requirement 11.4). It applies a
 * fixed-window request budget per client IP: within each window a client may
 * make up to {@code requestsPerWindow} requests; the request that exceeds the
 * budget receives HTTP 429 with the standard {@link ErrorResponse} envelope.
 *
 * <p>No third-party library is used &mdash; the state is a plain
 * {@link ConcurrentHashMap} of client IP to a small mutable window record, and
 * windows are rolled by comparing timestamps against
 * {@code System.nanoTime()}. The map is pruned lazily as entries are touched and
 * bounded defensively so a burst of distinct IPs cannot grow it without limit.
 *
 * <p>The client IP is derived {@code X-Forwarded-For}-aware (first hop) with a
 * fallback to {@link HttpServletRequest#getRemoteAddr()}, matching a deployment
 * behind Nginx.
 *
 * <p>All configuration is passed as derived primitives via constructor injection,
 * so this web-adapter class never depends on the {@code config} layer directly.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    /** Defensive cap on the number of tracked client keys. */
    private static final int MAX_TRACKED_CLIENTS = 100_000;

    private final boolean enabled;
    private final int requestsPerWindow;
    private final long windowNanos;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param enabled           whether the limiter is active
     * @param requestsPerWindow maximum requests per window per client; must be &gt;= 1
     * @param windowSeconds     fixed window length in seconds; must be &gt;= 1
     * @param objectMapper      shared JSON mapper for the 429 body; must not be null
     */
    public RateLimitFilter(
            boolean enabled, int requestsPerWindow, int windowSeconds, ObjectMapper objectMapper) {
        if (requestsPerWindow < 1) {
            throw new IllegalArgumentException("requestsPerWindow must be >= 1, was: " + requestsPerWindow);
        }
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be >= 1, was: " + windowSeconds);
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.enabled = enabled;
        this.requestsPerWindow = requestsPerWindow;
        this.windowNanos = (long) windowSeconds * 1_000_000_000L;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || allowed(clientIp(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        rejectWithTooManyRequests(response);
    }

    /**
     * Registers one request against the client's current window and reports
     * whether it is within budget. Thread-safe via per-key atomic updates.
     *
     * @param clientIp the resolved client identifier
     * @return {@code true} when the request is allowed, {@code false} when the
     *         budget for the current window is exhausted
     */
    private boolean allowed(String clientIp) {
        long now = System.nanoTime();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
        Window window = windows.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStartNanos >= windowNanos) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= requestsPerWindow;
    }

    private void rejectWithTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                "RATE_LIMITED",
                "Too many requests. Please slow down and try again shortly.");
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * Resolves the client IP, honoring the first hop of {@code X-Forwarded-For}
     * when present (deployment sits behind Nginx) and falling back to the socket
     * remote address otherwise.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma >= 0 ? forwardedFor.substring(0, comma) : forwardedFor;
            String trimmed = first.strip();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /** Mutable per-client fixed window: a start timestamp and a request counter. */
    private static final class Window {
        private final long windowStartNanos;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long windowStartNanos) {
            this.windowStartNanos = windowStartNanos;
        }
    }
}
