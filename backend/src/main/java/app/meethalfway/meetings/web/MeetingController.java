package app.meethalfway.meetings.web;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.application.RecommendationService;
import app.meethalfway.meetings.application.MeetingService;
import app.meethalfway.meetings.web.dto.CreateMeetingRequest;
import app.meethalfway.meetings.web.dto.EditMeetingRequest;
import app.meethalfway.meetings.web.dto.MeetingResponse;
import app.meethalfway.meetings.web.dto.RecommendationResponse;
import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.meetings.web.dto.WebMapper;
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
import org.springframework.web.bind.annotation.RequestMapping;
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
 * <p>The geocoding endpoints live on
 * {@code app.meethalfway.locations.web.LocationController}, keeping these
 * concerns in separate controllers.
 */
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final RecommendationService recommendationService;
    private final WebMapper webMapper;

    /**
     * @param meetingService         meeting CRUD operations
     * @param recommendationService recommendation operation; must not be null
     * @param webMapper              DTO/domain mapper; must not be null
     */
    public MeetingController(
            MeetingService meetingService,
            RecommendationService recommendationService,
            WebMapper webMapper) {
        this.meetingService = meetingService;
        this.recommendationService = recommendationService;
        this.webMapper = webMapper;
    }

    /**
     * Creates a meeting (Requirement 1, 9.1). Returns 201 with the created
     * meeting.
     *
     * @param request the validated create request
     * @return 201 Created with the meeting body
     */
    @PostMapping("")
    public ResponseEntity<MeetingResponse> create(@Valid @RequestBody CreateMeetingRequest request) {
        List<MeetingService.NewParticipant> participants =
                webMapper.toNewParticipants(request.participants());
        TransportMode mode = webMapper.toTransportMode(request.transportMode());
        Meeting created = meetingService.create(participants, mode);
        return ResponseEntity.status(HttpStatus.CREATED).body(webMapper.toMeetingResponse(created));
    }

    /**
     * Retrieves a meeting by its access code (Requirement 9.2, 9.3). Returns 200
     * with the meeting (including any stored recommendation) or 404 when unknown.
     *
     * @param code the meeting access code
     * @return 200 with the meeting body, or 404 when not found
     */
    @GetMapping("/{code}")
    public ResponseEntity<MeetingResponse> get(@PathVariable("code") String code) {
        return meetingService.byUrlCode(code)
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
    @PutMapping("/{code}")
    public ResponseEntity<MeetingResponse> edit(
            @PathVariable("code") String code, @Valid @RequestBody EditMeetingRequest request) {
        List<MeetingService.NewParticipant> participants =
                webMapper.toNewParticipants(request.participants());
        TransportMode mode = webMapper.toTransportMode(request.transportMode());
        Meeting updated = meetingService.edit(code, participants, mode);
        return ResponseEntity.ok(webMapper.toMeetingResponse(updated));
    }

    /**
     * Deletes a meeting by its access code (Requirement 9.5). Returns 204;
     * deleting an unknown code is a no-op.
     *
     * @param code the meeting access code
     * @return 204 No Content
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable("code") String code) {
        meetingService.deleteByUrlCode(code);
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
    @PostMapping("/{code}/recommendations")
    public ResponseEntity<?> computeRecommendations(@PathVariable("code") String code) {
        RecommendationOutcome outcome = recommendationService.compute(code);
        if (outcome instanceof RecommendationOutcome.Success success) {
            RecommendationResponse body = webMapper.toRecommendationResponse(success);
            return ResponseEntity.ok(body);
        }
        RecommendationOutcome.RoutingFailure failure = (RecommendationOutcome.RoutingFailure) outcome;
        RoutingFailureResponse body = webMapper.toRoutingFailureResponse(failure);
        return ResponseEntity.unprocessableEntity().body(body);
    }
}
