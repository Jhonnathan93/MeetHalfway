package app.meethalfway.meetings.web;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.application.ComputeRecommendations;
import app.meethalfway.meetings.application.CreateMeeting;
import app.meethalfway.meetings.application.DeleteMeeting;
import app.meethalfway.meetings.application.EditMeeting;
import app.meethalfway.meetings.application.GetMeeting;
import app.meethalfway.meetings.web.dto.CreateMeetingRequest;
import app.meethalfway.meetings.web.dto.EditMeetingRequest;
import app.meethalfway.meetings.web.dto.MeetingResponse;
import app.meethalfway.meetings.web.dto.RecommendationResponse;
import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.meetings.web.dto.WebMapper;
import app.meethalfway.shared.web.RouteRegistry;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for meetings and recommendations (Requirements 1, 6, 7,
 * 8, 9). Exposes the versioned surface under {@code /api/v1} with kebab-case
 * path segments and camelCase JSON. It delegates to the framework-free
 * application use cases and never contains business logic itself; all inputs are
 * validated at the boundary and mapped through {@link WebMapper}.
 *
 * <p>Dependencies are supplied by constructor injection only (no field
 * injection); the controller depends solely on application use cases and the web
 * mapper &mdash; never on the {@code config} layer or a provider adapter.
 *
 * <p>The meetings endpoints are anchored on the module's owned base path via
 * {@link RouteRegistry#MEETINGS_BASE_PATH}, so the registry and the annotations
 * share one source of truth for the base path (Requirement 3.2). The geocoding
 * endpoints ({@code /geocode/*}) now live on
 * {@code app.meethalfway.locations.web.LocationController}, so no single
 * controller serves both concerns (R2.5). No externally observable route changes.
 */
@RestController
public class MeetingController {

    private final CreateMeeting createMeeting;
    private final GetMeeting getMeeting;
    private final EditMeeting editMeeting;
    private final DeleteMeeting deleteMeeting;
    private final ComputeRecommendations computeRecommendations;
    private final WebMapper webMapper;

    /**
     * @param createMeeting          create use case; must not be null
     * @param getMeeting             get use case; must not be null
     * @param editMeeting            edit use case; must not be null
     * @param deleteMeeting          delete use case; must not be null
     * @param computeRecommendations compute use case; must not be null
     * @param webMapper              DTO/domain mapper; must not be null
     */
    public MeetingController(
            CreateMeeting createMeeting,
            GetMeeting getMeeting,
            EditMeeting editMeeting,
            DeleteMeeting deleteMeeting,
            ComputeRecommendations computeRecommendations,
            WebMapper webMapper) {
        if (createMeeting == null) {
            throw new IllegalArgumentException("createMeeting must not be null");
        }
        if (getMeeting == null) {
            throw new IllegalArgumentException("getMeeting must not be null");
        }
        if (editMeeting == null) {
            throw new IllegalArgumentException("editMeeting must not be null");
        }
        if (deleteMeeting == null) {
            throw new IllegalArgumentException("deleteMeeting must not be null");
        }
        if (computeRecommendations == null) {
            throw new IllegalArgumentException("computeRecommendations must not be null");
        }
        if (webMapper == null) {
            throw new IllegalArgumentException("webMapper must not be null");
        }
        this.createMeeting = createMeeting;
        this.getMeeting = getMeeting;
        this.editMeeting = editMeeting;
        this.deleteMeeting = deleteMeeting;
        this.computeRecommendations = computeRecommendations;
        this.webMapper = webMapper;
    }

    /**
     * Creates a meeting (Requirement 1, 9.1). Returns 201 with the created
     * meeting.
     *
     * @param request the validated create request
     * @return 201 Created with the meeting body
     */
    @PostMapping(RouteRegistry.MEETINGS_BASE_PATH)
    public ResponseEntity<MeetingResponse> create(@Valid @RequestBody CreateMeetingRequest request) {
        List<CreateMeeting.NewParticipant> participants =
                webMapper.toNewParticipants(request.participants());
        TransportMode mode = webMapper.toTransportMode(request.transportMode());
        Meeting created = createMeeting.create(participants, mode);
        return ResponseEntity.status(HttpStatus.CREATED).body(webMapper.toMeetingResponse(created));
    }

    /**
     * Retrieves a meeting by its access code (Requirement 9.2, 9.3). Returns 200
     * with the meeting (including any stored recommendation) or 404 when unknown.
     *
     * @param code the meeting access code
     * @return 200 with the meeting body, or 404 when not found
     */
    @GetMapping(RouteRegistry.MEETINGS_BASE_PATH + "/{code}")
    public ResponseEntity<MeetingResponse> get(@PathVariable("code") String code) {
        return getMeeting.byUrlCode(code)
                .map(webMapper::toMeetingResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("no meeting found for url code: " + code));
    }

    /**
     * Edits an existing meeting (Requirement 9.2, 9.4). Returns 200 with the
     * updated meeting; 404 (via {@link NoSuchElementException}) when the code is
     * unknown. Editing clears any stored recommendation.
     *
     * @param code    the meeting access code
     * @param request the validated edit request
     * @return 200 with the updated meeting body
     */
    @PutMapping(RouteRegistry.MEETINGS_BASE_PATH + "/{code}")
    public ResponseEntity<MeetingResponse> edit(
            @PathVariable("code") String code, @Valid @RequestBody EditMeetingRequest request) {
        List<CreateMeeting.NewParticipant> participants =
                webMapper.toNewParticipants(request.participants());
        TransportMode mode = webMapper.toTransportMode(request.transportMode());
        Meeting updated = editMeeting.edit(code, participants, mode);
        return ResponseEntity.ok(webMapper.toMeetingResponse(updated));
    }

    /**
     * Deletes a meeting by its access code (Requirement 9.5). Returns 204;
     * deleting an unknown code is a no-op.
     *
     * @param code the meeting access code
     * @return 204 No Content
     */
    @DeleteMapping(RouteRegistry.MEETINGS_BASE_PATH + "/{code}")
    public ResponseEntity<Void> delete(@PathVariable("code") String code) {
        deleteMeeting.byUrlCode(code);
        return ResponseEntity.noContent().build();
    }

    /**
     * Computes recommendations for a meeting (Requirements 6, 7, 8). On success
     * returns 200 with the three strategies (and the outlier trade-off when
     * present). On a routing failure returns 422 with an actionable body listing
     * each affected participant location so the Creator can correct or remove it
     * &mdash; a participant is never silently dropped (Requirement 6). Returns
     * 404 when the meeting is unknown.
     *
     * @param code the meeting access code
     * @return 200 with the recommendation, or 422 with the routing-failure body
     */
    @PostMapping(RouteRegistry.MEETINGS_BASE_PATH + "/{code}/recommendations")
    public ResponseEntity<?> computeRecommendations(@PathVariable("code") String code) {
        RecommendationOutcome outcome = computeRecommendations.compute(code);
        if (outcome instanceof RecommendationOutcome.Success success) {
            RecommendationResponse body = webMapper.toRecommendationResponse(success);
            return ResponseEntity.ok(body);
        }
        RecommendationOutcome.RoutingFailure failure = (RecommendationOutcome.RoutingFailure) outcome;
        RoutingFailureResponse body = webMapper.toRoutingFailureResponse(failure);
        return ResponseEntity.unprocessableEntity().body(body);
    }
}
