package app.meethalfway.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import app.meethalfway.domain.port.MeetingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: meeting-recommendation-engine, Property 17: Meeting persistence
 * round-trip
 *
 * <p>For any valid meeting (2–10 participants with UUID-string participant ids,
 * in-range coordinates, either transport mode, and with or without a stored
 * recommendation outcome), saving it through {@link JpaMeetingRepository} and
 * loading it back by URL code yields an equivalent aggregate: identical
 * participant count, names, locations, transport mode, and recommendation
 * outcome (including the outlier trade-off when present).
 *
 * <p>Validates: Requirement 9.1–9.4.
 *
 * <p>Because the {@code per_participant_times} column is PostgreSQL {@code JSONB}
 * and no offline database (Testcontainers/H2) is available, this test exercises
 * the real adapter + mapper against a hand-written in-memory fake of the Spring
 * Data repository ({@link InMemorySpringDataMeetingRepository}). No new
 * dependencies and no infrastructure are required.
 */
class MeetingPersistenceRoundTripPropertyTest {

    /** Fixed UTC clock: timestamps are not part of the round-trip assertions. */
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Property(tries = 100)
    void savingAndLoadingYieldsAnEquivalentMeeting(@ForAll("meetings") Meeting original) {
        MeetingRepository repository = new JpaMeetingRepository(
                new InMemorySpringDataMeetingRepository(), new MeetingMapper(), clock);

        repository.save(original);
        Optional<Meeting> loaded = repository.findByUrlCode(original.urlCode());

        assertThat(loaded).isPresent();
        assertMeetingEquivalent(original, loaded.orElseThrow());
    }

    private static void assertMeetingEquivalent(Meeting expected, Meeting actual) {
        assertThat(actual.urlCode()).isEqualTo(expected.urlCode());
        assertThat(actual.input().mode()).isEqualTo(expected.input().mode());

        List<ParticipantInput> expectedParticipants = expected.input().participants();
        List<ParticipantInput> actualParticipants = actual.input().participants();
        assertThat(actualParticipants).hasSameSizeAs(expectedParticipants);
        for (int i = 0; i < expectedParticipants.size(); i++) {
            ParticipantInput e = expectedParticipants.get(i);
            ParticipantInput a = actualParticipants.get(i);
            assertThat(a.id()).isEqualTo(e.id());
            assertThat(a.name()).isEqualTo(e.name());
            assertThat(a.location()).isEqualTo(e.location());
        }

        assertThat(actual.recommendation().isPresent())
                .isEqualTo(expected.recommendation().isPresent());
        expected.recommendation().ifPresent(expectedOutcome ->
                assertOutcomeEquivalent(expectedOutcome, actual.recommendation().orElseThrow()));
    }

    private static void assertOutcomeEquivalent(
            RecommendationOutcome expected, RecommendationOutcome actual) {
        RecommendationOutcome.Success expectedSuccess = (RecommendationOutcome.Success) expected;
        RecommendationOutcome.Success actualSuccess = (RecommendationOutcome.Success) actual;

        assertResultsEquivalent(expectedSuccess.results(), actualSuccess.results());
        assertThat(actualSuccess.tradeoff().isPresent())
                .isEqualTo(expectedSuccess.tradeoff().isPresent());
        expectedSuccess.tradeoff().ifPresent(expectedTradeoff -> {
            OutlierTradeoff actualTradeoff = actualSuccess.tradeoff().orElseThrow();
            assertThat(actualTradeoff.outliers()).isEqualTo(expectedTradeoff.outliers());
            assertResultsEquivalent(expectedTradeoff.including(), actualTradeoff.including());
            assertResultsEquivalent(expectedTradeoff.excluding(), actualTradeoff.excluding());
            assertThat(actualTradeoff.avgTravelTimeIncluding())
                    .isEqualTo(expectedTradeoff.avgTravelTimeIncluding());
            assertThat(actualTradeoff.avgTravelTimeExcluding())
                    .isEqualTo(expectedTradeoff.avgTravelTimeExcluding());
        });
    }

    private static void assertResultsEquivalent(StrategyResults expected, StrategyResults actual) {
        assertResultEquivalent(expected.fastest(), actual.fastest());
        assertResultEquivalent(expected.minimax(), actual.minimax());
        assertResultEquivalent(expected.fairest(), actual.fairest());
    }

    private static void assertResultEquivalent(StrategyResult expected, StrategyResult actual) {
        assertThat(actual.point()).isEqualTo(expected.point());
        assertThat(actual.perParticipant()).isEqualTo(expected.perParticipant());
        assertThat(actual.sumTime()).isEqualTo(expected.sumTime());
        assertThat(actual.maxTime()).isEqualTo(expected.maxTime());
        assertThat(actual.stdDev()).isEqualTo(expected.stdDev());
    }

    // ---- Generators -----------------------------------------------------

    @Provide
    Arbitrary<Meeting> meetings() {
        Arbitrary<List<ParticipantInput>> participants = participantList();
        Arbitrary<TransportMode> modes = Arbitraries.of(TransportMode.DRIVING, TransportMode.WALKING);
        return Combinators.combine(participants, modes)
                .as((list, mode) -> new MeetingInput(list, mode))
                .flatMap(this::meetingWithOptionalRecommendation);
    }

    private Arbitrary<Meeting> meetingWithOptionalRecommendation(MeetingInput input) {
        String urlCode = "M-" + UUID.randomUUID();
        Arbitrary<Optional<RecommendationOutcome>> outcomes = recommendation(input);
        return outcomes.map(outcome -> new Meeting(urlCode, input, outcome));
    }

    private Arbitrary<List<ParticipantInput>> participantList() {
        return participant().list().ofMinSize(2).ofMaxSize(10);
    }

    private Arbitrary<ParticipantInput> participant() {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(20);
        Arbitrary<Double> lats = Arbitraries.doubles().between(-89.0, 89.0);
        Arbitrary<Double> lngs = Arbitraries.doubles().between(-179.0, 179.0);
        return Combinators.combine(names, lats, lngs).as((name, lat, lng) ->
                new ParticipantInput(
                        new ParticipantId(UUID.randomUUID().toString()),
                        name,
                        new Coordinate(lat, lng)));
    }

    /**
     * Generates one of three recommendation states with roughly equal weight:
     * none, a success without an outlier trade-off, or a success with a trade-off.
     * The strategy results are synthetic but structurally valid; equivalence, not
     * optimality, is what the round-trip asserts.
     */
    private Arbitrary<Optional<RecommendationOutcome>> recommendation(MeetingInput input) {
        List<ParticipantId> ids = new ArrayList<>();
        for (ParticipantInput participant : input.participants()) {
            ids.add(participant.id());
        }
        return Arbitraries.of(RecommendationKind.values()).map(kind -> switch (kind) {
            case NONE -> Optional.empty();
            case SUCCESS_NO_TRADEOFF ->
                    Optional.of(RecommendationOutcome.Success.of(results(ids, 3)));
            case SUCCESS_WITH_TRADEOFF -> outcomeWithTradeoff(ids);
        });
    }

    private Optional<RecommendationOutcome> outcomeWithTradeoff(List<ParticipantId> ids) {
        // One outlier is dropped in the excluding variant; keep at least one.
        ParticipantId outlier = ids.get(ids.size() - 1);
        List<ParticipantId> kept = new ArrayList<>(ids);
        kept.remove(outlier);

        StrategyResults including = results(ids, 5);
        StrategyResults excluding = results(kept, 4);
        Set<ParticipantId> outliers = new LinkedHashSet<>();
        outliers.add(outlier);
        double avgIncluding = meanMinutes(including.fastest());
        double avgExcluding = meanMinutes(excluding.fastest());
        OutlierTradeoff tradeoff =
                new OutlierTradeoff(outliers, including, excluding, avgIncluding, avgExcluding);
        return Optional.of(RecommendationOutcome.Success.of(including, tradeoff));
    }

    private static double meanMinutes(StrategyResult result) {
        double sum = 0.0;
        for (Minutes minutes : result.perParticipant().values()) {
            sum += minutes.value();
        }
        return sum / result.perParticipant().size();
    }

    private StrategyResults results(List<ParticipantId> ids, int baseMinutes) {
        return new StrategyResults(
                strategyResult(ids, baseMinutes, 10.10, 20.20),
                strategyResult(ids, baseMinutes + 1, 11.11, 21.21),
                strategyResult(ids, baseMinutes + 2, 12.12, 22.22));
    }

    private StrategyResult strategyResult(List<ParticipantId> ids, int baseMinutes, double lat, double lng) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        int sum = 0;
        int max = 0;
        int offset = 0;
        for (ParticipantId id : ids) {
            int minutes = baseMinutes + offset;
            perParticipant.put(id, new Minutes(minutes));
            sum += minutes;
            max = Math.max(max, minutes);
            offset++;
        }
        return new StrategyResult(new Coordinate(lat, lng), perParticipant, sum, max, 1.5);
    }

    private enum RecommendationKind {
        NONE,
        SUCCESS_NO_TRADEOFF,
        SUCCESS_WITH_TRADEOFF
    }
}
