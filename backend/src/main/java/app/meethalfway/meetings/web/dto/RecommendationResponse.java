package app.meethalfway.meetings.web.dto;

import app.meethalfway.meetings.web.dto.OutlierTradeoffResponse;
import java.util.List;

/**
 * Wire representation of a successful recommendation (Requirements 7, 8).
 *
 * <p>Carries the three strategy results and, when at least one outlier was
 * detected, the include-versus-exclude trade-off ({@code outlierTradeoff} is
 * {@code null} when there is no outlier). Routing failures are <em>not</em>
 * represented here; they are surfaced as an HTTP 422 error envelope so a
 * participant is never silently dropped (Requirement 6).
 *
 * <p><strong>Additive display metadata (Requirements 10.7, 10.9, 10.8, 12.4):</strong>
 * {@code warnings} was added to surface coordinates that fell outside the valid
 * latitude/longitude ranges without rejecting the whole response. It defaults to
 * an empty list (never {@code null}) when there are no out-of-range coordinates,
 * so the change is backward-compatible: no existing field was removed or renamed.
 *
 * <p>JSON fields are camelCase.
 *
 * @param results         the three strategy results
 * @param outlierTradeoff the outlier trade-off, or {@code null} when absent
 * @param warnings        out-of-range candidate/participant-origin warnings; never
 *                        {@code null}, empty when there are none
 */
public record RecommendationResponse(
        StrategyResultsResponse results,
        OutlierTradeoffResponse outlierTradeoff,
        List<CoordinateWarningResponse> warnings) {

    /**
     * Normalizes {@code warnings} to an empty, immutable list when {@code null}
     * is supplied, so the field is never {@code null} on the wire.
     */
    public RecommendationResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Convenience factory for the common case of no out-of-range coordinates.
     *
     * @param results         the three strategy results
     * @param outlierTradeoff the outlier trade-off, or {@code null} when absent
     * @return a response whose {@code warnings} is the empty list
     */
    public static RecommendationResponse of(
            StrategyResultsResponse results, OutlierTradeoffResponse outlierTradeoff) {
        return new RecommendationResponse(results, outlierTradeoff, List.of());
    }
}
