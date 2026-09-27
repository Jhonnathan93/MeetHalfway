package app.meethalfway.adapters.persistence;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.OutlierTradeoff;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.StrategyResult;
import app.meethalfway.domain.model.StrategyResults;
import app.meethalfway.domain.model.TransportMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Translates between the framework-free {@link Meeting} aggregate and its JPA
 * entities ({@link MeetingEntity}, {@link ParticipantEntity},
 * {@link RecommendationEntity}). Keeping this mapping in one explicit,
 * well-named class (never an ambiguous "Utils") preserves the Clean Architecture
 * boundary: the domain stays free of persistence concerns and the entities stay
 * free of domain semantics.
 *
 * <h2>ParticipantId ↔ UUID contract</h2>
 * The {@code participant} table's primary key is a {@link UUID}, and the schema
 * has no separate column for the domain {@link ParticipantId}. To make the
 * round-trip lossless with zero schema change, this mapper adopts the contract
 * that a domain {@code ParticipantId.value} is the string form of the
 * participant's UUID primary key:
 * <ul>
 *   <li>{@link #toEntity(Meeting)} parses {@code participant.id().value()} with
 *       {@link UUID#fromString(String)} and uses it as the entity PK;</li>
 *   <li>{@link #toDomain(MeetingEntity)} rebuilds the {@code ParticipantId} from
 *       {@code entity.getId().toString()}.</li>
 * </ul>
 * The {@code CreateMeeting} use case therefore mints participant ids as UUID
 * strings so persistence is lossless. A non-UUID {@code ParticipantId} value is
 * a programming error at the persistence boundary and fails fast with a clear
 * message rather than silently deriving a lossy surrogate.
 *
 * <h2>Recommendation rows</h2>
 * Only a {@link RecommendationOutcome.Success} is persisted; a
 * {@code RoutingFailure} is never stored. A success with no outlier trade-off
 * maps to three rows ({@code excludesOutlier=false}). A success with a trade-off
 * maps to six rows: the three {@code including} rows ({@code excludesOutlier=
 * false}) and the three {@code excluding} rows ({@code excludesOutlier=true}).
 * The engine builds {@code Success.results()} to equal {@code tradeoff.including()},
 * so the top-level results are persisted as the {@code excludesOutlier=false}
 * rows and reconstructed symmetrically.
 */
public final class MeetingMapper {

    private static final String FASTEST = "FASTEST";
    private static final String MINIMAX = "MINIMAX";
    private static final String FAIREST = "FAIREST";

    /**
     * Builds a fresh, unpersisted {@link MeetingEntity} graph from a domain
     * {@link Meeting}. The caller (the repository adapter) supplies persistence
     * identity and timestamps because those are lifecycle concerns owned by the
     * adapter, not the mapper. Participant PKs are derived from the domain
     * {@code ParticipantId} values (see the class contract).
     *
     * @param meeting   the domain aggregate to map; must not be {@code null}
     * @param id        the meeting entity primary key
     * @param createdAt the creation timestamp
     * @param updatedAt the last-update timestamp
     * @return a fully-populated entity graph ready to persist
     */
    public MeetingEntity toEntity(
            Meeting meeting,
            UUID id,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt) {
        if (meeting == null) {
            throw new IllegalArgumentException("meeting must not be null");
        }
        MeetingEntity entity = new MeetingEntity(
                id,
                meeting.urlCode(),
                toWireMode(meeting.input().mode()),
                createdAt,
                updatedAt);
        populateChildren(entity, meeting);
        return entity;
    }

    /**
     * Rewrites an existing {@link MeetingEntity} in place to match the given
     * domain {@link Meeting}, preserving the entity's identity and creation
     * timestamp. Existing participant and recommendation children are cleared and
     * rebuilt (orphan removal deletes the old rows), and the transport mode and
     * URL code are refreshed. The caller is responsible for bumping
     * {@code updatedAt}.
     *
     * @param entity  the managed entity to update; must not be {@code null}
     * @param meeting the desired domain state; must not be {@code null}
     */
    public void updateEntity(MeetingEntity entity, Meeting meeting) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        if (meeting == null) {
            throw new IllegalArgumentException("meeting must not be null");
        }
        entity.setUrlCode(meeting.urlCode());
        entity.setTransportMode(toWireMode(meeting.input().mode()));
        entity.getParticipants().clear();
        entity.getRecommendations().clear();
        populateChildren(entity, meeting);
    }

    private void populateChildren(MeetingEntity entity, Meeting meeting) {
        for (ParticipantInput participant : meeting.input().participants()) {
            entity.addParticipant(new ParticipantEntity(
                    toParticipantUuid(participant.id()),
                    participant.name(),
                    participant.location().lat(),
                    participant.location().lng()));
        }
        meeting.recommendation().ifPresent(outcome -> addRecommendationRows(entity, outcome));
    }

    private void addRecommendationRows(MeetingEntity entity, RecommendationOutcome outcome) {
        // Only a Success is persisted; a RoutingFailure is never stored as a
        // recommendation (Requirement 6). Silently ignore a RoutingFailure here
        // because CreateMeeting/ComputeRecommendations never attach one.
        if (!(outcome instanceof RecommendationOutcome.Success success)) {
            return;
        }
        // Including rows (excludesOutlier=false) come from the top-level results,
        // which the engine guarantees equal tradeoff.including() when a trade-off
        // exists.
        addStrategyRows(entity, success.results(), false);
        success.tradeoff().ifPresent(tradeoff ->
                addStrategyRows(entity, tradeoff.excluding(), true));
    }

    private void addStrategyRows(MeetingEntity entity, StrategyResults results, boolean excludesOutlier) {
        entity.addRecommendation(toRecommendationEntity(results.fastest(), FASTEST, excludesOutlier));
        entity.addRecommendation(toRecommendationEntity(results.minimax(), MINIMAX, excludesOutlier));
        entity.addRecommendation(toRecommendationEntity(results.fairest(), FAIREST, excludesOutlier));
    }

    private RecommendationEntity toRecommendationEntity(
            StrategyResult result, String strategy, boolean excludesOutlier) {
        return new RecommendationEntity(
                UUID.randomUUID(),
                strategy,
                excludesOutlier,
                result.point().lat(),
                result.point().lng(),
                result.sumTime(),
                result.maxTime(),
                result.stdDev(),
                toPerParticipantTimes(result.perParticipant()));
    }

    private Map<String, Integer> toPerParticipantTimes(Map<ParticipantId, Minutes> perParticipant) {
        Map<String, Integer> wire = new LinkedHashMap<>();
        for (Map.Entry<ParticipantId, Minutes> entry : perParticipant.entrySet()) {
            wire.put(entry.getKey().value(), entry.getValue().value());
        }
        return wire;
    }

    /**
     * Reconstructs a domain {@link Meeting} from its entity graph, inverting
     * {@link #toEntity}. The reconstructed aggregate is equivalent to the
     * original: same participant count, names, locations, transport mode, and
     * recommendation outcome.
     *
     * @param entity the persisted entity graph; must not be {@code null}
     * @return the equivalent domain aggregate
     */
    public Meeting toDomain(MeetingEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        List<ParticipantInput> participants = new ArrayList<>();
        for (ParticipantEntity participant : entity.getParticipants()) {
            participants.add(new ParticipantInput(
                    new ParticipantId(participant.getId().toString()),
                    participant.getName(),
                    new Coordinate(participant.getLat(), participant.getLng())));
        }
        MeetingInput input = new MeetingInput(participants, fromWireMode(entity.getTransportMode()));

        Optional<RecommendationOutcome> recommendation = toRecommendation(entity.getRecommendations());
        return new Meeting(entity.getUrlCode(), input, recommendation);
    }

    private Optional<RecommendationOutcome> toRecommendation(List<RecommendationEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        List<RecommendationEntity> including = new ArrayList<>();
        List<RecommendationEntity> excluding = new ArrayList<>();
        for (RecommendationEntity row : rows) {
            if (row.isExcludesOutlier()) {
                excluding.add(row);
            } else {
                including.add(row);
            }
        }
        StrategyResults includingResults = toStrategyResults(including);
        if (excluding.isEmpty()) {
            return Optional.of(RecommendationOutcome.Success.of(includingResults));
        }
        StrategyResults excludingResults = toStrategyResults(excluding);
        OutlierTradeoff tradeoff = reconstructTradeoff(includingResults, excludingResults);
        return Optional.of(RecommendationOutcome.Success.of(includingResults, tradeoff));
    }

    /**
     * Rebuilds an equivalent {@link OutlierTradeoff}. The set of outliers is the
     * participants present in the including results but absent from the excluding
     * results (the engine drops the outlier id(s) from every excluding vector).
     * The two group averages are recomputed as the mean of the respective Fastest
     * winner's per-participant times, matching how the engine derived them, so the
     * reconstructed trade-off is equivalent to the original.
     */
    private OutlierTradeoff reconstructTradeoff(StrategyResults including, StrategyResults excluding) {
        Set<ParticipantId> includingIds = including.fastest().perParticipant().keySet();
        Set<ParticipantId> excludingIds = excluding.fastest().perParticipant().keySet();
        Set<ParticipantId> outliers = new LinkedHashSet<>(includingIds);
        outliers.removeAll(excludingIds);

        double avgIncluding = meanMinutes(including.fastest());
        double avgExcluding = meanMinutes(excluding.fastest());
        return new OutlierTradeoff(outliers, including, excluding, avgIncluding, avgExcluding);
    }

    private double meanMinutes(StrategyResult result) {
        Map<ParticipantId, Minutes> perParticipant = result.perParticipant();
        double sum = 0.0;
        for (Minutes minutes : perParticipant.values()) {
            sum += minutes.value();
        }
        return sum / perParticipant.size();
    }

    private StrategyResults toStrategyResults(List<RecommendationEntity> rows) {
        StrategyResult fastest = null;
        StrategyResult minimax = null;
        StrategyResult fairest = null;
        for (RecommendationEntity row : rows) {
            StrategyResult result = toStrategyResult(row);
            switch (row.getStrategy()) {
                case FASTEST -> fastest = result;
                case MINIMAX -> minimax = result;
                case FAIREST -> fairest = result;
                default -> throw new IllegalStateException(
                        "unknown strategy in persistence: " + row.getStrategy());
            }
        }
        if (fastest == null || minimax == null || fairest == null) {
            throw new IllegalStateException(
                    "incomplete recommendation set; expected FASTEST, MINIMAX and FAIREST rows");
        }
        return new StrategyResults(fastest, minimax, fairest);
    }

    private StrategyResult toStrategyResult(RecommendationEntity row) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : row.getPerParticipantTimes().entrySet()) {
            perParticipant.put(new ParticipantId(entry.getKey()), new Minutes(entry.getValue()));
        }
        return new StrategyResult(
                new Coordinate(row.getPointLat(), row.getPointLng()),
                perParticipant,
                row.getSumTime(),
                row.getMaxTime(),
                row.getStdDev());
    }

    private UUID toParticipantUuid(ParticipantId id) {
        try {
            return UUID.fromString(id.value());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "participant id must be a UUID string for lossless persistence, was: "
                            + id.value(), ex);
        }
    }

    private String toWireMode(TransportMode mode) {
        return switch (mode) {
            case DRIVING -> TransportMode.DRIVING_VALUE;
            case WALKING -> TransportMode.WALKING_VALUE;
        };
    }

    private TransportMode fromWireMode(String wire) {
        return TransportMode.fromValue(wire);
    }
}
