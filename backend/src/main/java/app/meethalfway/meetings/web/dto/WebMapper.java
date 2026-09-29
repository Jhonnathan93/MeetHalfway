package app.meethalfway.meetings.web.dto;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierTradeoff;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.application.MeetingService;
import app.meethalfway.shared.web.dto.CoordinateResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Explicit, stateless mapper between web DTOs, application inputs, and domain
 * types (Requirement 11.2). Domain records are never serialized directly: their
 * component names, {@code Optional} wrappers, and value objects do not match the
 * camelCase, flat JSON contract, so every conversion is spelled out here.
 *
 * <p>This is a named mapper class with explicit methods rather than an ambiguous
 * utility grab-bag. It is framework-free and holds no state, so it is trivially
 * safe to share as a singleton bean.
 */
@Component
public final class WebMapper {

    /** Strategy key exposed as {@code candidateId} for the Fastest strategy. */
    private static final String STRATEGY_FASTEST = "fastest";

    /** Strategy key exposed as {@code candidateId} for the Minimax strategy. */
    private static final String STRATEGY_MINIMAX = "minimax";

    /** Strategy key exposed as {@code candidateId} for the Fairest strategy. */
    private static final String STRATEGY_FAIREST = "fairest";

    /**
     * Sanitizes and converts request participants into application
     * {@link MeetingService.NewParticipant} inputs. The display name is sanitized
     * with {@link NameSanitizer}; the coordinate is built from the validated
     * lat/lng. Domain value objects perform the final range enforcement.
     *
     * @param participants the validated request participants; must not be null
     * @return the application-layer new-participant inputs
     */
    public List<MeetingService.NewParticipant> toNewParticipants(List<ParticipantRequest> participants) {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        List<MeetingService.NewParticipant> result = new ArrayList<>(participants.size());
        for (ParticipantRequest participant : participants) {
            if (participant == null) {
                throw new IllegalArgumentException("participants must not contain a null entry");
            }
            String name = NameSanitizer.sanitize(participant.name());
            Coordinate location = new Coordinate(participant.lat(), participant.lng());
            result.add(new MeetingService.NewParticipant(name, location));
        }
        return result;
    }

    /**
     * Parses a transport-mode wire value into the domain {@link TransportMode},
     * accepting only {@code driving}/{@code walking}. An unsupported value
     * throws {@link IllegalArgumentException} (surfaced as a 400 naming the
     * supported modes).
     *
     * @param wireValue the transport-mode wire value
     * @return the domain transport mode
     */
    public TransportMode toTransportMode(String wireValue) {
        return TransportMode.fromValue(wireValue);
    }

    /**
     * Maps a domain {@link Meeting} aggregate to its wire representation,
     * including any stored recommendation.
     *
     * @param meeting the domain meeting; must not be null
     * @return the meeting response
     */
    public MeetingResponse toMeetingResponse(Meeting meeting) {
        if (meeting == null) {
            throw new IllegalArgumentException("meeting must not be null");
        }
        List<ParticipantResponse> participants = new ArrayList<>(meeting.input().participants().size());
        for (ParticipantInput participant : meeting.input().participants()) {
            participants.add(new ParticipantResponse(
                    participant.id().value(),
                    participant.name(),
                    toCoordinateResponse(participant.location())));
        }
        String transportMode = toWireMode(meeting.input().mode());
        RecommendationResponse recommendation = meeting.recommendation()
                .map(this::toRecommendationResponse)
                .orElse(null);
        return new MeetingResponse(meeting.urlCode(), participants, transportMode, recommendation);
    }

    /**
     * Maps a successful outcome to a {@link RecommendationResponse}, including
     * the outlier trade-off when present.
     *
     * @param success the successful outcome; must not be null
     * @return the recommendation response with the legacy {@code warnings}
     *         field empty; domain coordinates are range-validated on construction
     */
    public RecommendationResponse toRecommendationResponse(RecommendationOutcome.Success success) {
        if (success == null) {
            throw new IllegalArgumentException("success must not be null");
        }
        StrategyResultsResponse results = toStrategyResultsResponse(success.results());
        OutlierTradeoffResponse tradeoff = success.tradeoff()
                .map(this::toOutlierTradeoffResponse)
                .orElse(null);
        return RecommendationResponse.of(results, tradeoff);
    }

    /**
     * Maps a routing failure to the HTTP 422 envelope, attaching an actionable
     * top-level message asking the Creator to correct or remove the affected
     * location(s) (Requirement 6.2).
     *
     * @param failure the routing failure; must not be null
     * @return the routing-failure response body
     */
    public RoutingFailureResponse toRoutingFailureResponse(RecommendationOutcome.RoutingFailure failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }
        List<RoutingErrorResponse> errors = new ArrayList<>(failure.errors().size());
        failure.errors().forEach(error -> errors.add(new RoutingErrorResponse(
                error.participantId().value(),
                toCoordinateResponse(error.location()),
                error.reason())));
        String message = "We could not calculate travel time for "
                + errors.size() + (errors.size() == 1 ? " location. " : " locations. ")
                + "Please correct or remove the listed location(s) and try again.";
        return new RoutingFailureResponse("ROUTING_FAILURE", message, errors);
    }

    /** Maps the three strategy results to their wire representation. */
    private StrategyResultsResponse toStrategyResultsResponse(StrategyResults results) {
        return new StrategyResultsResponse(
                toStrategyResultResponse(results.fastest(), STRATEGY_FASTEST),
                toStrategyResultResponse(results.minimax(), STRATEGY_MINIMAX),
                toStrategyResultResponse(results.fairest(), STRATEGY_FAIREST));
    }

    /**
     * Maps one strategy result to its wire form, stamping the {@code candidateId}
     * with the strategy key and marking it {@code recommended} (each strategy
     * returns exactly one selected point, which is its recommended point).
     */
    private StrategyResultResponse toStrategyResultResponse(
            StrategyResult result, String candidateId) {
        Coordinate point = result.point();
        Map<String, Integer> perParticipant = new LinkedHashMap<>();
        for (Map.Entry<ParticipantId, Minutes> entry : result.perParticipant().entrySet()) {
            perParticipant.put(entry.getKey().value(), entry.getValue().value());
        }
        return new StrategyResultResponse(
                toCoordinateResponse(point),
                perParticipant,
                result.sumTime(),
                result.maxTime(),
                result.stdDev(),
                candidateId,
                true);
    }

    private OutlierTradeoffResponse toOutlierTradeoffResponse(OutlierTradeoff tradeoff) {
        List<String> outliers = new ArrayList<>(tradeoff.outliers().size());
        tradeoff.outliers().forEach(id -> outliers.add(id.value()));
        return new OutlierTradeoffResponse(
                outliers,
                toStrategyResultsResponse(tradeoff.including()),
                toStrategyResultsResponse(tradeoff.excluding()),
                tradeoff.avgTravelTimeIncluding(),
                tradeoff.avgTravelTimeExcluding());
    }

    /**
     * Maps a domain {@link Coordinate} to its wire representation, used for
     * participant locations and routing-error locations.
     *
     * @param coordinate the domain coordinate; must not be null
     * @return the coordinate response
     */
    public CoordinateResponse toCoordinateResponse(Coordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("coordinate must not be null");
        }
        return new CoordinateResponse(coordinate.lat(), coordinate.lng());
    }

    private String toWireMode(TransportMode mode) {
        return switch (mode) {
            case DRIVING -> TransportMode.DRIVING_VALUE;
            case WALKING -> TransportMode.WALKING_VALUE;
        };
    }

}
