package app.meethalfway.domain.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.EvaluatedCandidate;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.OutlierTradeoff;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.RoutingError;
import app.meethalfway.domain.model.StrategyResult;
import app.meethalfway.domain.model.StrategyResults;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.RouteResult;
import app.meethalfway.domain.port.RoutingProvider;

/**
 * Default {@link RecommendationEngine} that wires the pure engine components into
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
 *       candidate points from the participant origins via the injected
 *       {@link CandidateGenerator} (Requirement 5.1).</li>
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
 *   <li>re-evaluates the <em>same</em> candidate set with the outlier
 *       participant id(s) removed from each candidate's per-participant map
 *       (metrics recomputed via {@link MetricCalculator}; the candidate set is
 *       unchanged, so the computation stays deterministic), and re-runs all three
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
public final class DefaultRecommendationEngine implements RecommendationEngine {

    private final MeetingValidator meetingValidator;
    private final CandidateGenerator candidateGenerator;
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
    public DefaultRecommendationEngine(
            MeetingValidator meetingValidator,
            CandidateGenerator candidateGenerator,
            MetricCalculator metricCalculator,
            OutlierDetector outlierDetector) {
        if (meetingValidator == null) {
            throw new IllegalArgumentException("meetingValidator must not be null");
        }
        if (candidateGenerator == null) {
            throw new IllegalArgumentException("candidateGenerator must not be null");
        }
        if (metricCalculator == null) {
            throw new IllegalArgumentException("metricCalculator must not be null");
        }
        if (outlierDetector == null) {
            throw new IllegalArgumentException("outlierDetector must not be null");
        }
        this.meetingValidator = meetingValidator;
        this.candidateGenerator = candidateGenerator;
        this.metricCalculator = metricCalculator;
        this.outlierDetector = outlierDetector;
    }

    @Override
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

        // 3. Route every participant to every candidate; surface routing failures.
        List<RoutingError> routingErrors = collectRoutingErrors(participants, candidates, routing, input.mode());
        if (!routingErrors.isEmpty()) {
            return new RecommendationOutcome.RoutingFailure(routingErrors);
        }

        // 4. Capture each candidate's raw per-participant travel times once, so the
        //    excluding variant can be re-evaluated from the same matrix (deterministic,
        //    no extra routing).
        List<Map<ParticipantId, Minutes>> travelTimeMatrix =
                routeMatrix(participants, candidates, routing, input.mode());

        // 5. Evaluate metrics per candidate and run the three selectors, tie-broken by
        //    the config's ε and centroid, over the full (including-outlier) group.
        List<EvaluatedCandidate> evaluated = evaluateAll(candidates, travelTimeMatrix);
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

        StrategyResults excluding =
                selectExcluding(candidates, travelTimeMatrix, outliers, participants, tieBreaker);

        double avgIncluding = metricCalculator.meanTime(fastestIncluding.perParticipant());
        double avgExcluding = metricCalculator.meanTime(excluding.fastest().perParticipant());

        OutlierTradeoff tradeoff = new OutlierTradeoff(
                outliers, including, excluding, avgIncluding, avgExcluding);
        return RecommendationOutcome.Success.of(including, tradeoff);
    }

    /**
     * Routes every participant to every candidate and returns one
     * {@link RoutingError} per participant whose location cannot be routed. A
     * participant is short-circuited on their first failure (their location is
     * unroutable regardless of the candidate) so each affected participant is
     * reported exactly once (Requirement 6.1). Returns an empty list when every
     * participant routes to every candidate.
     */
    private List<RoutingError> collectRoutingErrors(
            List<ParticipantInput> participants,
            List<Coordinate> candidates,
            RoutingProvider routing,
            TransportMode mode) {
        List<RoutingError> errors = new ArrayList<>();
        for (ParticipantInput participant : participants) {
            Coordinate origin = participant.location();
            for (Coordinate candidate : candidates) {
                RouteResult result = routing.travelTime(origin, candidate, mode);
                if (result instanceof RouteResult.Failure failure) {
                    errors.add(new RoutingError(participant.id(), origin, failure.reason()));
                    break; // this participant's location is unroutable; report once.
                }
            }
        }
        return errors;
    }

    /**
     * Routes every participant to every candidate and returns, per candidate (in
     * candidate order), the map of participant id to travel time. Only called
     * after {@link #collectRoutingErrors} has confirmed every route succeeds, so
     * every {@link RouteResult} here is a {@link RouteResult.Success}. Capturing
     * the full matrix once lets the excluding variant be recomputed from it with
     * no extra routing, keeping the computation deterministic.
     */
    private List<Map<ParticipantId, Minutes>> routeMatrix(
            List<ParticipantInput> participants,
            List<Coordinate> candidates,
            RoutingProvider routing,
            TransportMode mode) {
        List<Map<ParticipantId, Minutes>> matrix = new ArrayList<>(candidates.size());
        for (Coordinate candidate : candidates) {
            Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
            for (ParticipantInput participant : participants) {
                RouteResult result = routing.travelTime(participant.location(), candidate, mode);
                RouteResult.Success success = (RouteResult.Success) result;
                perParticipant.put(participant.id(), success.travelTime());
            }
            matrix.add(perParticipant);
        }
        return matrix;
    }

    /**
     * Builds an {@link EvaluatedCandidate} for every candidate from its captured
     * per-participant travel times (metrics via {@link MetricCalculator}).
     */
    private List<EvaluatedCandidate> evaluateAll(
            List<Coordinate> candidates, List<Map<ParticipantId, Minutes>> travelTimeMatrix) {
        List<EvaluatedCandidate> evaluated = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            evaluated.add(metricCalculator.evaluate(candidates.get(i), travelTimeMatrix.get(i)));
        }
        return evaluated;
    }

    /**
     * Recomputes the three strategy results over the <em>same</em> candidate set
     * with the outlier participant id(s) removed from each candidate's
     * per-participant map, then re-run the selectors. The centroid is recomputed
     * over the non-outlier origins so the final tie-break stays consistent with
     * the reduced group.
     */
    private StrategyResults selectExcluding(
            List<Coordinate> candidates,
            List<Map<ParticipantId, Minutes>> travelTimeMatrix,
            Set<ParticipantId> outliers,
            List<ParticipantInput> participants,
            TieBreaker tieBreaker) {
        List<EvaluatedCandidate> reduced = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Map<ParticipantId, Minutes> full = travelTimeMatrix.get(i);
            Map<ParticipantId, Minutes> kept = new LinkedHashMap<>();
            for (Map.Entry<ParticipantId, Minutes> entry : full.entrySet()) {
                if (!outliers.contains(entry.getKey())) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
            reduced.add(metricCalculator.evaluate(candidates.get(i), kept));
        }

        // Centroid over the non-outlier origins keeps the final tie-break consistent
        // with the reduced group the excluding variant represents.
        List<Coordinate> keptOrigins = new ArrayList<>();
        for (ParticipantInput participant : participants) {
            if (!outliers.contains(participant.id())) {
                keptOrigins.add(participant.location());
            }
        }
        Coordinate centroid = GeographicCentroid.of(keptOrigins);
        return selectAll(reduced, centroid, tieBreaker);
    }

    /**
     * Runs the Fastest, Minimax, and Fairest selectors over the evaluated
     * candidates and maps each winner to a {@link StrategyResult}. Selectors are
     * built here from a {@link TieBreaker} carrying {@code config.epsilonMinutes()}
     * so ε stays config-driven.
     */
    private StrategyResults selectAll(
            List<EvaluatedCandidate> evaluated, Coordinate centroid, TieBreaker tieBreaker) {
        EvaluatedCandidate fastest = new FastestSelector(tieBreaker).select(evaluated, centroid);
        EvaluatedCandidate minimax = new MinimaxSelector(tieBreaker).select(evaluated, centroid);
        EvaluatedCandidate fairest = new FairestSelector(tieBreaker).select(evaluated, centroid);

        return new StrategyResults(
                toStrategyResult(fastest),
                toStrategyResult(minimax),
                toStrategyResult(fairest));
    }

    private static StrategyResult toStrategyResult(EvaluatedCandidate candidate) {
        return new StrategyResult(
                candidate.point(),
                candidate.perParticipant(),
                candidate.sumTime(),
                candidate.maxTime(),
                candidate.stdDev());
    }
}
