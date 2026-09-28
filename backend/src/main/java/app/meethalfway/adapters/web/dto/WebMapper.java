package app.meethalfway.adapters.web.dto;

import app.meethalfway.application.CreateMeeting;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.OutlierTradeoff;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.StrategyResult;
import app.meethalfway.domain.model.StrategyResults;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.AddressSuggestion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public final class WebMapper {

    /**
     * Sanitizes and converts request participants into application
     * {@link CreateMeeting.NewParticipant} inputs. The display name is sanitized
     * with {@link NameSanitizer}; the coordinate is built from the validated
     * lat/lng. Domain value objects perform the final range enforcement.
     *
     * @param participants the validated request participants; must not be null
     * @return the application-layer new-participant inputs
     */
    public List<CreateMeeting.NewParticipant> toNewParticipants(List<ParticipantRequest> participants) {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        List<CreateMeeting.NewParticipant> result = new ArrayList<>(participants.size());
        for (ParticipantRequest participant : participants) {
            if (participant == null) {
                throw new IllegalArgumentException("participants must not contain a null entry");
            }
            String name = NameSanitizer.sanitize(participant.name());
            Coordinate location = new Coordinate(participant.lat(), participant.lng());
            result.add(new CreateMeeting.NewParticipant(name, location));
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
                .filter(RecommendationOutcome.Success.class::isInstance)
                .map(RecommendationOutcome.Success.class::cast)
                .map(this::toRecommendationResponse)
                .orElse(null);
        return new MeetingResponse(meeting.urlCode(), participants, transportMode, recommendation);
    }

    /**
     * Maps a successful outcome to a {@link RecommendationResponse}, including
     * the outlier trade-off when present.
     *
     * @param success the successful outcome; must not be null
     * @return the recommendation response
     */
    public RecommendationResponse toRecommendationResponse(RecommendationOutcome.Success success) {
        if (success == null) {
            throw new IllegalArgumentException("success must not be null");
        }
        StrategyResultsResponse results = toStrategyResultsResponse(success.results());
        OutlierTradeoffResponse tradeoff = success.tradeoff()
                .map(this::toOutlierTradeoffResponse)
                .orElse(null);
        return new RecommendationResponse(results, tradeoff);
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

    /**
     * Maps geocoding suggestions to their wire representation. No provider key is
     * ever carried on a suggestion.
     *
     * @param suggestions the domain suggestions; must not be null
     * @return the suggestion responses
     */
    public List<AddressSuggestionResponse> toSuggestionResponses(List<AddressSuggestion> suggestions) {
        if (suggestions == null) {
            throw new IllegalArgumentException("suggestions must not be null");
        }
        List<AddressSuggestionResponse> result = new ArrayList<>(suggestions.size());
        suggestions.forEach(suggestion ->
                result.add(new AddressSuggestionResponse(suggestion.description(), suggestion.placeId())));
        return result;
    }

    private StrategyResultsResponse toStrategyResultsResponse(StrategyResults results) {
        return new StrategyResultsResponse(
                toStrategyResultResponse(results.fastest()),
                toStrategyResultResponse(results.minimax()),
                toStrategyResultResponse(results.fairest()));
    }

    private StrategyResultResponse toStrategyResultResponse(StrategyResult result) {
        Map<String, Integer> perParticipant = new LinkedHashMap<>();
        for (Map.Entry<ParticipantId, Minutes> entry : result.perParticipant().entrySet()) {
            perParticipant.put(entry.getKey().value(), entry.getValue().value());
        }
        return new StrategyResultResponse(
                toCoordinateResponse(result.point()),
                perParticipant,
                result.sumTime(),
                result.maxTime(),
                result.stdDev());
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
     * Maps a domain {@link Coordinate} to its wire representation. Exposed so the
     * geocoding resolve endpoint can return a resolved location directly.
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

    /**
     * Exposes the {@link Optional} unwrapping used when a caller already has an
     * outcome and wants the success mapping without re-checking the variant.
     *
     * @param outcome an optional outcome
     * @return the mapped recommendation response, or empty when absent/not success
     */
    public Optional<RecommendationResponse> toRecommendationResponse(
            Optional<RecommendationOutcome> outcome) {
        return outcome
                .filter(RecommendationOutcome.Success.class::isInstance)
                .map(RecommendationOutcome.Success.class::cast)
                .map(this::toRecommendationResponse);
    }
}
