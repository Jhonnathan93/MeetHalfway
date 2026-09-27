package app.meethalfway.adapters.web.dto;

/**
 * Wire representation of a successful recommendation (Requirements 7, 8).
 *
 * <p>Carries the three strategy results and, when at least one outlier was
 * detected, the include-versus-exclude trade-off ({@code outlierTradeoff} is
 * {@code null} when there is no outlier). Routing failures are <em>not</em>
 * represented here; they are surfaced as an HTTP 422 error envelope so a
 * participant is never silently dropped (Requirement 6).
 *
 * <p>JSON fields are camelCase.
 *
 * @param results         the three strategy results
 * @param outlierTradeoff the outlier trade-off, or {@code null} when absent
 */
public record RecommendationResponse(
        StrategyResultsResponse results,
        OutlierTradeoffResponse outlierTradeoff) {
}
