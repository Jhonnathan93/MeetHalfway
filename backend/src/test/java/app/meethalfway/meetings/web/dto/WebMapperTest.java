package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.Meeting;
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
import app.meethalfway.meetings.application.MeetingService;
import app.meethalfway.meetings.web.dto.MeetingResponse;
import app.meethalfway.meetings.web.dto.ParticipantRequest;
import app.meethalfway.meetings.web.dto.RecommendationResponse;
import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.meetings.web.dto.WebMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WebMapper} (Task 12.1/12.2). Verifies explicit mapping
 * between domain types and the camelCase wire DTOs, including the sanitize-first
 * participant conversion, the wire transport-mode form, the outlier trade-off
 * mapping (creator-decides: the comparison is surfaced, never auto-applied), and
 * the actionable routing-failure envelope.
 */
class WebMapperTest {

    private final WebMapper mapper = new WebMapper();

    private static StrategyResult sampleResult(int minutes) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(minutes));
        perParticipant.put(new ParticipantId("p2"), new Minutes(minutes + 2));
        return new StrategyResult(
                new Coordinate(6.24, -75.57),
                perParticipant,
                (minutes * 2) + 2, minutes + 2, 1.0);
    }

    private static <T> List<T> mutable(List<T> items) {
        return new ArrayList<>(items);
    }

    @Test
    void sanitizesNamesWhenBuildingNewParticipants() {
        List<MeetingService.NewParticipant> result = mapper.toNewParticipants(List.of(
                new ParticipantRequest("  <b>Ana</b>  ", 6.24, -75.58),
                new ParticipantRequest(null, 6.25, -75.56)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("bAna/b");
        assertThat(result.get(1).name()).isEmpty();
        assertThat(result.get(0).location()).isEqualTo(new Coordinate(6.24, -75.58));
    }

    @Test
    void mapsTransportModeToWireFormInMeetingResponse() {
        MeetingInput input = new MeetingInput(
                mutable(List.of(
                        new ParticipantInput(new ParticipantId("p1"), "Ana", new Coordinate(6.24, -75.58)),
                        new ParticipantInput(new ParticipantId("p2"), "Bruno", new Coordinate(6.25, -75.56)))),
                TransportMode.WALKING);
        MeetingResponse response = mapper.toMeetingResponse(Meeting.of("CODE1234", input));

        assertThat(response.transportMode()).isEqualTo("walking");
        assertThat(response.participants()).hasSize(2);
        assertThat(response.recommendation()).isNull();
    }

    @Test
    void surfacesOutlierTradeoffWithoutApplyingIt() {
        StrategyResults results = new StrategyResults(
                sampleResult(10), sampleResult(11), sampleResult(9));
        Set<ParticipantId> outliers = new LinkedHashSet<>();
        outliers.add(new ParticipantId("p2"));
        OutlierTradeoff tradeoff = new OutlierTradeoff(
                outliers,
                results, results, 12.0, 8.0);
        RecommendationOutcome.Success success =
                RecommendationOutcome.Success.of(results, tradeoff);

        RecommendationResponse response = mapper.toRecommendationResponse(success);

        // Both including and excluding computations are present: the Creator
        // decides, the engine never silently drops the outlier.
        assertThat(response.outlierTradeoff()).isNotNull();
        assertThat(response.outlierTradeoff().outliers()).containsExactly("p2");
        assertThat(response.outlierTradeoff().avgTravelTimeIncluding()).isEqualTo(12.0);
        assertThat(response.outlierTradeoff().avgTravelTimeExcluding()).isEqualTo(8.0);
    }

    @Test
    void mapsRoutingFailureToActionableEnvelope() {
        RecommendationOutcome.RoutingFailure failure = new RecommendationOutcome.RoutingFailure(mutable(List.of(
                new RoutingError(new ParticipantId("p2"), new Coordinate(6.25, -75.56), "unreachable"))));

        RoutingFailureResponse response = mapper.toRoutingFailureResponse(failure);

        assertThat(response.code()).isEqualTo("ROUTING_FAILURE");
        assertThat(response.message()).contains("correct or remove");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).participantId()).isEqualTo("p2");
        assertThat(response.errors().get(0).reason()).isEqualTo("unreachable");
    }

    @Test
    void meetingResponseIncludesStoredSuccessRecommendation() {
        MeetingInput input = new MeetingInput(
                mutable(List.of(
                        new ParticipantInput(new ParticipantId("p1"), "Ana", new Coordinate(6.24, -75.58)),
                        new ParticipantInput(new ParticipantId("p2"), "Bruno", new Coordinate(6.25, -75.56)))),
                TransportMode.DRIVING);
        StrategyResults results = new StrategyResults(
                sampleResult(10), sampleResult(11), sampleResult(9));
        Meeting meeting = new Meeting("CODE1234", input,
                Optional.of(RecommendationOutcome.Success.of(results)));

        MeetingResponse response = mapper.toMeetingResponse(meeting);

        assertThat(response.recommendation()).isNotNull();
        assertThat(response.recommendation().results().fastest().point().lat()).isEqualTo(6.24);
    }
}
