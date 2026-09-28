package app.meethalfway.meetings.web.dto;

/**
 * Wire representation of the three strategy results produced by one computation
 * (Requirement 8.1). JSON fields are camelCase.
 *
 * @param fastest the Fastest strategy result
 * @param minimax the Minimax strategy result
 * @param fairest the Fairest strategy result
 */
public record StrategyResultsResponse(
        StrategyResultResponse fastest,
        StrategyResultResponse minimax,
        StrategyResultResponse fairest) {
}
