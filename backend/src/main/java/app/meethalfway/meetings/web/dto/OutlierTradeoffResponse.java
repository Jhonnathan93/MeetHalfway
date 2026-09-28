package app.meethalfway.meetings.web.dto;

import java.util.List;

/**
 * Wire representation of the include-versus-exclude outlier trade-off
 * (Requirement 7). Present only when at least one outlier was detected; the
 * engine never drops an outlier silently, so both computations are always
 * returned together and the Creator decides.
 *
 * <p>JSON fields are camelCase.
 *
 * @param outliers                the ids of the participants flagged as outliers
 * @param including               the three strategy results with outliers included
 * @param excluding               the three strategy results with outliers excluded
 * @param avgTravelTimeIncluding  group average travel time for the including case
 * @param avgTravelTimeExcluding  group average travel time for the excluding case
 */
public record OutlierTradeoffResponse(
        List<String> outliers,
        StrategyResultsResponse including,
        StrategyResultsResponse excluding,
        double avgTravelTimeIncluding,
        double avgTravelTimeExcluding) {
}
