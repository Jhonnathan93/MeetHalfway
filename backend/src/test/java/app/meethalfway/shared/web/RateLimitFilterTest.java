package app.meethalfway.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Focused unit tests for the hand-rolled {@link RateLimitFilter} (Task 12.4).
 *
 * <p>Drives the filter directly with mock servlet request/response objects so the
 * fixed-window budget, the 429 envelope, per-IP isolation, and the
 * {@code X-Forwarded-For} awareness are all exercised without starting a server.
 */
class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static FilterChain countingChain(int[] counter) {
        return (request, response) -> counter[0]++;
    }

    @Test
    void allowsRequestsUpToTheBudgetThenReturns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 3, 60, objectMapper);
        int[] passed = {0};
        FilterChain chain = countingChain(passed);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(passed[0]).isEqualTo(3);

        MockHttpServletRequest overLimit = new MockHttpServletRequest();
        overLimit.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(overLimit, blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).contains("application/json");
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
        // The chain was not invoked for the blocked request.
        assertThat(passed[0]).isEqualTo(3);
    }

    @Test
    void tracksBudgetPerClientIpIndependently() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 60, objectMapper);
        int[] passed = {0};
        FilterChain chain = countingChain(passed);

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletRequest a = new MockHttpServletRequest();
        a.setRemoteAddr("1.1.1.1");
        filter.doFilter(a, first, chain);
        assertThat(first.getStatus()).isEqualTo(200);

        // A different IP has its own fresh budget.
        MockHttpServletResponse second = new MockHttpServletResponse();
        MockHttpServletRequest b = new MockHttpServletRequest();
        b.setRemoteAddr("2.2.2.2");
        filter.doFilter(b, second, chain);
        assertThat(second.getStatus()).isEqualTo(200);

        // The first IP is now over budget.
        MockHttpServletResponse third = new MockHttpServletResponse();
        MockHttpServletRequest aAgain = new MockHttpServletRequest();
        aAgain.setRemoteAddr("1.1.1.1");
        filter.doFilter(aAgain, third, chain);
        assertThat(third.getStatus()).isEqualTo(429);
    }

    @Test
    void usesFirstXForwardedForHopWhenPresent() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 60, objectMapper);
        int[] passed = {0};
        FilterChain chain = countingChain(passed);

        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr("10.0.0.99");
        first.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.99");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain);
        assertThat(firstResponse.getStatus()).isEqualTo(200);

        // Same forwarded client, different proxy remote addr -> still same bucket.
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setRemoteAddr("10.0.0.100");
        second.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, chain);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void disabledFilterAlwaysDelegates() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(false, 1, 60, objectMapper);
        int[] passed = {0};
        FilterChain chain = countingChain(passed);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("9.9.9.9");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(passed[0]).isEqualTo(5);
    }

    @Test
    void constructorRejectsInvalidArguments() {
        assertThatThrownBy(() -> new RateLimitFilter(true, 0, 60, objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitFilter(true, 1, 0, objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitFilter(true, 1, 60, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
