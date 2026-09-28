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
import app.meethalfway.meetings.application.CreateMeeting;
import app.meethalfway.shared.web.dto.CoordinateResponse;
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

    /** Strategy key exposed as {@code candidateId} for the Fastest strategy (Requirement 10.2). */
    private static final String STRATEGY_FASTEST = "fastest";

    /** Strategy key exposed as {@code candidateId} for the Minimax strategy (Requirement 10.2). */
    private static final String STRATEGY_MINIMAX = "minimax";

    /** Strategy key exposed as {@code candidateId} for the Fairest strategy (Requirement 10.2). */
    private static final String STRATEGY_FAIREST = "fairest";

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

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
        List<ParticipantInput> origins = meeting.input().participants();
        RecommendationResponse recommendation = meeting.recommendation()
                .filter(RecommendationOutcome.Success.class::isInstance)
                .map(RecommendationOutcome.Success.class::cast)
                .map(success -> toRecommendationResponse(success, origins))
                .orElse(null);
        return new MeetingResponse(meeting.urlCode(), participants, transportMode, recommendation);
    }

    /**
     * Maps a successful outcome to a {@link RecommendationResponse}, including
     * the outlier trade-off when present.
     *
     * <p>This overload carries no participant origins, so it reports only
     * out-of-range <em>candidate</em> warnings (Requirement 10.7). The
     * recommendations endpoint uses it; the meeting-response path uses
     * {@link #toRecommendationResponse(RecommendationOutcome.Success, java.util.List)}
     * so it can also report out-of-range participant origins (Requirement 10.9).
     *
     * @param success the successful outcome; must not be null
     * @return the recommendation response, with an empty {@code warnings} list
     *         when every candidate coordinate is in range
     */
    public RecommendationResponse toRecommendationResponse(RecommendationOutcome.Success success) {
        return toRecommendationResponse(success, List.of());
    }

    /**
     * Maps a successful outcome to a {@link RecommendationResponse}, additionally
     * validating the supplied participant origins so any out-of-range origin is
     * reported as a warning rather than silently omitted (Requirement 10.9).
     *
     * <p>Out-of-range candidate points are excluded from {@code results} (that
     * strategy slot becomes {@code null}) and each is reported as a warning; the
     * remaining valid candidates are still returned (Requirement 10.7). The
     * {@code warnings} list is never {@code null} and is empty when every
     * coordinate is in range.
     *
     * @param success           the successful outcome; must not be null
     * @param participantOrigins the meeting's participant inputs whose origins are
     *                           range-checked; must not be null (may be empty)
     * @return the recommendation response with display metadata and warnings
     */
    public RecommendationResponse toRecommendationResponse(
            RecommendationOutcome.Success success, List<ParticipantInput> participantOrigins) {
        if (success == null) {
            throw new IllegalArgumentException("success must not be null");
        }
        if (participantOrigins == null) {
            throw new IllegalArgumentException("participantOrigins must not be null");
        }
        List<CoordinateWarningResponse> warnings = new ArrayList<>();
        StrategyResultsResponse results = toStrategyResultsResponse(success.results(), warnings);
        OutlierTradeoffResponse tradeoff = success.tradeoff()
                .map(this::toOutlierTradeoffResponse)
                .orElse(null);
        addParticipantOriginWarnings(participantOrigins, warnings);
        return new RecommendationResponse(results, tradeoff, warnings);
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
     * Maps the three strategy results without collecting warnings. Used for the
     * outlier trade-off's nested copies, whose candidate points mirror the main
     * results, so their out-of-range coordinates are already reported once via
     * the top-level {@code results} mapping (no duplicate warnings).
     */
    private StrategyResultsResponse toStrategyResultsResponse(StrategyResults results) {
        return new StrategyResultsResponse(
                toStrategyResultResponse(results.fastest(), STRATEGY_FASTEST, null),
                toStrategyResultResponse(results.minimax(), STRATEGY_MINIMAX, null),
                toStrategyResultResponse(results.fairest(), STRATEGY_FAIREST, null));
    }

    /**
     * Maps the three strategy results, appending a warning and excluding the
     * offending strategy slot (leaving it {@code null}) for any candidate point
     * whose coordinate is out of range (Requirement 10.7).
     */
    private StrategyResultsResponse toStrategyResultsResponse(
            StrategyResults results, List<CoordinateWarningResponse> warnings) {
        return new StrategyResultsResponse(
                toStrategyResultResponse(results.fastest(), STRATEGY_FASTEST, warnings),
                toStrategyResultResponse(results.minimax(), STRATEGY_MINIMAX, warnings),
                toStrategyResultResponse(results.fairest(), STRATEGY_FAIREST, warnings));
    }

    /**
     * Maps one strategy result to its wire form, stamping the {@code candidateId}
     * with the strategy key and marking it {@code recommended} (each strategy
     * returns exactly one selected point, which is its recommended point,
     * Requirement 10.4). When {@code warnings} is non-null and the candidate
     * coordinate is out of range, the point is excluded (returns {@code null})
     * and a warning is appended (Requirement 10.7).
     */
    private StrategyResultResponse toStrategyResultResponse(
            StrategyResult result, String candidateId, List<CoordinateWarningResponse> warnings) {
        Coordinate point = result.point();
        if (warnings != null && !isInRange(point)) {
            warnings.add(outOfRangeWarning(
                    CoordinateWarningResponse.KIND_CANDIDATE, candidateId, point));
            return null;
        }
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

    /**
     * Appends an out-of-range warning for each participant origin whose
     * coordinate is invalid, so it is reported rather than silently omitted
     * (Requirement 10.9).
     */
    private void addParticipantOriginWarnings(
            List<ParticipantInput> participants, List<CoordinateWarningResponse> warnings) {
        for (ParticipantInput participant : participants) {
            if (participant == null) {
                continue;
            }
            Coordinate location = participant.location();
            if (!isInRange(location)) {
                warnings.add(outOfRangeWarning(
                        CoordinateWarningResponse.KIND_PARTICIPANT_ORIGIN,
                        participant.id().value(),
                        location));
            }
        }
    }

    private static boolean isInRange(Coordinate coordinate) {
        double lat = coordinate.lat();
        double lng = coordinate.lng();
        return lat >= MIN_LATITUDE && lat <= MAX_LATITUDE
                && lng >= MIN_LONGITUDE && lng <= MAX_LONGITUDE;
    }

    private static CoordinateWarningResponse outOfRangeWarning(
            String kind, String reference, Coordinate coordinate) {
        return new CoordinateWarningResponse(
                kind, reference, coordinate.lat(), coordinate.lng(), rangeReason(coordinate));
    }

    private static String rangeReason(Coordinate coordinate) {
        double lat = coordinate.lat();
        double lng = coordinate.lng();
        if (lat < MIN_LATITUDE || lat > MAX_LATITUDE) {
            return "latitude " + lat + " outside [" + MIN_LATITUDE + "," + MAX_LATITUDE + "]";
        }
        return "longitude " + lng + " outside [" + MIN_LONGITUDE + "," + MAX_LONGITUDE + "]";
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
