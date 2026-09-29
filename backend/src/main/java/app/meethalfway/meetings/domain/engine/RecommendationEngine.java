package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierTradeoff;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.RoutingError;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * Wires the pure engine components into
 * a single deterministic computation (Requirements 2.2, 3.1, 4.4, 6.1, 6.3, 6.4,
 * 8.1, 8.2, 8.3).
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li><b>Validate</b> — participant count and coordinate ranges are already
 *       guaranteed by {@link MeetingInput}/{@link Coordinate} construction; this
 *       engine additionally checks service bounds via {@link MeetingValidator}
 *       (Requirement 1.7).</li>
 *   <li><b>Generate candidates</b> — exactly {@code config.gridDensityN()}
 *       candidate points from the participant origins via the grid generator
 *       (Requirement 5.1).</li>
 *   <li><b>Route</b> — for every participant &times; every candidate, query the
 *       injected {@link RoutingProvider}. If routing fails for any participant
 *       location, that location is unroutable: the engine collects one
 *       {@link RoutingError} per affected participant and returns a
 *       {@link RecommendationOutcome.RoutingFailure}. It never produces a
 *       {@code Success} that omits a participant (Requirements 6.1, 6.3,
 *       6.4).</li>
 *   <li><b>Evaluate &amp; select</b> — when all routes succeed, compute the
 *       per-candidate metrics via {@link MetricCalculator}, compute the
 *       {@code Geographic_Centroid} of the participant origins, and run the
 *       Fastest, Minimax, and Fairest selectors (each built from a
 *       {@link TieBreaker} carrying {@code config.epsilonMinutes()}). Each winner
 *       maps to a {@link StrategyResult} (Requirements 2.2, 3.1, 4.4, 8).</li>
 * </ol>
 *
 * <h2>Outlier dual-computation (Requirement 7)</h2>
 * After selecting the three <em>including</em> strategies over the full
 * participant set, the engine flags outliers via the injected
 * {@link OutlierDetector}. The detector is applied to a single, well-defined
 * <b>reference travel-time vector</b>: the {@code Fastest} (including) winner's
 * per-participant times. That winner is the {@code Sum_Time}-optimal candidate
 * for the whole group, so its per-participant vector is the most representative
 * single snapshot of "how far each participant is" for outlier judgement, and it
 * is already computed (no extra routing).
 *
 * <p>When the reference set contains <b>no outlier</b>, the engine returns
 * {@link RecommendationOutcome.Success#of(StrategyResults)} with an empty
 * trade-off. When it contains <b>at least one</b> outlier, the engine:
 * <ol>
 *   <li>builds a new search region and candidate set using only the remaining
 *       participant origins, obtains a new routing matrix, and re-runs all three
 *       selectors to produce the <em>excluding</em> {@link StrategyResults};</li>
 *   <li>computes the two group-average travel times as the mean of the
 *       respective {@code Fastest} winner's per-participant vector —
 *       {@code avgTravelTimeIncluding} over all participants and
 *       {@code avgTravelTimeExcluding} over the non-outlier participants; and</li>
 *   <li>returns a {@code Success} whose {@code including} results are exactly the
 *       top-level results and whose trade-off carries both variants
 *       (Requirements 7.2, 7.3, 7.5). The primary results always keep the
 *       outlier in — the engine never drops an outlier silently.</li>
 * </ol>
 *
 * <p>Should removing the outlier(s) leave no participant (defensive guard; per
 * Requirement 7 an outlier is "exaggeratedly larger than the rest", so at least
 * one non-outlier always remains), the engine skips the excluding computation and
 * returns the including results with an empty trade-off rather than constructing
 * an invalid empty meeting.
 *
 * <h2>Routing-failure aggregation</h2>
 * A participant whose origin cannot be routed to a candidate cannot be routed at
 * all (their location is the problem, not the candidate), so the engine
 * short-circuits per participant: on the first failure for a participant it
 * records one {@link RoutingError} (with the participant id, location, and the
 * provider's specific reason) and moves to the next participant. This reports
 * each affected participant exactly once while still surfacing every distinct
 * failing location (Requirement 6.1).
 *
 * <p>Dependencies are supplied through explicit constructor injection; this class
 * is framework-free domain logic with no dependency on HTTP, persistence, or any
 * concrete provider.
 */
public final class RecommendationEngine {

    private final MeetingValidator meetingValidator;
    private final GridCandidateGenerator candidateGenerator;
    private final MetricCalculator metricCalculator;
    private final OutlierDetector outlierDetector;

    /**
     * Creates the engine with its pure collaborators.
     *
     * @param meetingValidator   validates service bounds (Requirement 1.7); must
     *                           not be null
     * @param candidateGenerator generates the Grid_Search candidates
     *                           (Requirement 5.1); must not be null
     * @param metricCalculator   computes per-candidate metrics (Requirement 5.2);
     *                           must not be null
     * @param outlierDetector    detects outliers (Requirement 7.1); injected now
     *                           and used by task 7.2; must not be null
     * @throws IllegalArgumentException if any dependency is null
     */
    public RecommendationEngine(
            MeetingValidator meetingValidator,
            GridCandidateGenerator candidateGenerator,
            MetricCalculator metricCalculator,
            OutlierDetector outlierDetector) {
        this.meetingValidator = meetingValidator;
        this.candidateGenerator = candidateGenerator;
        this.metricCalculator = metricCalculator;
        this.outlierDetector = outlierDetector;
    }

    public RecommendationOutcome compute(
            MeetingInput input, EngineConfig config, RoutingProvider routing) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (routing == null) {
            throw new IllegalArgumentException("routing must not be null");
        }

        // 1. Service-bounds validation (range invariants already hold by construction).
        meetingValidator.validate(input, config);

        List<ParticipantInput> participants = input.participants();
        List<Coordinate> origins = new ArrayList<>(participants.size());
        for (ParticipantInput participant : participants) {
            origins.add(participant.location());
        }

        // 2. Generate exactly N candidates from the participant origins.
        List<Coordinate> candidates = candidateGenerator.generate(origins, config);

        // 3. Capture each candidate's raw per-participant travel times once. This
        //    also detects routing failures, avoiding a separate preflight pass that
        //    would duplicate every external routing request.
        RoutingMatrix routingMatrix = routeMatrix(participants, candidates, routing, input.mode());
        if (!routingMatrix.errors().isEmpty()) {
            return new RecommendationOutcome.RoutingFailure(routingMatrix.errors());
        }

        // 4. Keep the full group's route matrix for its three strategy results.
        List<Map<ParticipantId, Minutes>> travelTimeMatrix = routingMatrix.travelTimes();

        // 5. Evaluate metrics per candidate and run the three selectors, tie-broken by
        //    the config's ε and centroid, over the full (including-outlier) group.
        List<StrategyResult> evaluated = evaluateAll(candidates, travelTimeMatrix);
        Coordinate centroid = GeographicCentroid.of(origins);
        TieBreaker tieBreaker = new TieBreaker(config.epsilonMinutes());
        StrategyResults including = selectAll(evaluated, centroid, tieBreaker);

        // 6. Outlier dual-computation (Requirement 7). The reference vector is the
        //    Fastest (including) winner's per-participant times.
        StrategyResult fastestIncluding = including.fastest();
        Set<ParticipantId> outliers =
                outlierDetector.detect(fastestIncluding.perParticipant(), config);

        if (outliers.isEmpty()) {
            return RecommendationOutcome.Success.of(including);
        }

        // Defensive guard: if excluding the outlier(s) would leave nobody, present the
        // including results with no trade-off rather than an invalid empty meeting.
        long remaining = participants.stream()
                .filter(participant -> !outliers.contains(participant.id()))
                .count();
        if (remaining == 0) {
            return RecommendationOutcome.Success.of(including);
        }

        List<ParticipantInput> keptParticipants = participants.stream()
                .filter(participant -> !outliers.contains(participant.id()))
                .toList();
        List<Coordinate> keptOrigins = keptParticipants.stream()
                .map(ParticipantInput::location)
                .toList();

        // The reduced group gets a fresh search region, candidate grid, routing
        // matrix, centroid and strategy selection. Reusing the full group's grid
        // can preserve the outlier's influence on the answer even after removing
        // their travel times.
        List<Coordinate> excludingCandidates = candidateGenerator.generate(keptOrigins, config);
        RoutingMatrix excludingRoutingMatrix = routeMatrix(
                keptParticipants, excludingCandidates, routing, input.mode());
        if (!excludingRoutingMatrix.errors().isEmpty()) {
            return new RecommendationOutcome.RoutingFailure(excludingRoutingMatrix.errors());
        }
        List<StrategyResult> excludingEvaluated = evaluateAll(
                excludingCandidates, excludingRoutingMatrix.travelTimes());
        Coordinate excludingCentroid = GeographicCentroid.of(keptOrigins);
        StrategyResults excluding = selectAll(excludingEvaluated, excludingCentroid, tieBreaker);

        double avgIncluding = metricCalculator.meanTime(fastestIncluding.perParticipant());
        double avgExcluding = metricCalculator.meanTime(excluding.fastest().perParticipant());

        OutlierTradeoff tradeoff = new OutlierTradeoff(
                outliers, including, excluding, avgIncluding, avgExcluding);
        return RecommendationOutcome.Success.of(including, tradeoff);
    }

    /**
     * Routes every participant to every candidate and returns, per candidate (in
     * candidate order), the map of participant id to travel time and any routing
     * errors. A participant is short-circuited on its first failure, so each
     * affected location is reported exactly once.
     */
    private RoutingMatrix routeMatrix(
            List<ParticipantInput> participants,
            List<Coordinate> candidates,
            RoutingProvider routing,
            TransportMode mode) {
        List<Coordinate> origins = participants.stream()
                .map(ParticipantInput::location)
                .toList();
        List<List<RouteResult>> routed = routing.travelTimes(origins, candidates, mode);
        if (routed.size() != participants.size()) {
            throw new IllegalStateException("routing provider returned an invalid origin count");
        }

        List<Map<ParticipantId, Minutes>> matrix = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            matrix.add(new LinkedHashMap<>());
        }
        List<RoutingError> errors = new ArrayList<>();
        for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
            ParticipantInput participant = participants.get(participantIndex);
            List<RouteResult> row = routed.get(participantIndex);
            if (row == null || row.size() != candidates.size()) {
                throw new IllegalStateException("routing provider returned an invalid destination count");
            }

            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                RouteResult result = row.get(candidateIndex);
                if (result == null) {
                    throw new IllegalStateException("routing provider returned a null route result");
                }
                if (result instanceof RouteResult.Success success) {
                    matrix.get(candidateIndex).put(participant.id(), success.travelTime());
                } else {
                    RouteResult.Failure failure = (RouteResult.Failure) result;
                    errors.add(new RoutingError(
                            participant.id(), participant.location(), failure.reason()));
                    break;
                }
            }
        }
        return new RoutingMatrix(matrix, errors);
    }

    private record RoutingMatrix(
            List<Map<ParticipantId, Minutes>> travelTimes, List<RoutingError> errors) {}

    /**
     * Builds an {@link StrategyResult} for every candidate from its captured
     * per-participant travel times (metrics via {@link MetricCalculator}).
     */
    private List<StrategyResult> evaluateAll(
            List<Coordinate> candidates, List<Map<ParticipantId, Minutes>> travelTimeMatrix) {
        List<StrategyResult> evaluated = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            evaluated.add(metricCalculator.evaluate(candidates.get(i), travelTimeMatrix.get(i)));
        }
        return evaluated;
    }

    /**
     * Runs the three strategy selectors over the evaluated candidates. Selectors are
     * built here from a {@link TieBreaker} carrying {@code config.epsilonMinutes()}
     * so ε stays config-driven.
     */
    private StrategyResults selectAll(
            List<StrategyResult> evaluated, Coordinate centroid, TieBreaker tieBreaker) {
        StrategyResult fastest = new FastestSelector(tieBreaker).select(evaluated, centroid);
        StrategyResult minimax = new MinimaxSelector(tieBreaker).select(evaluated, centroid);
        StrategyResult fairest = new FairestSelector(tieBreaker).select(evaluated, centroid);

        return new StrategyResults(fastest, minimax, fairest);
    }
}
