package app.meethalfway.adapters.web;

import app.meethalfway.adapters.web.dto.AddressSuggestionResponse;
import app.meethalfway.adapters.web.dto.CreateMeetingRequest;
import app.meethalfway.adapters.web.dto.EditMeetingRequest;
import app.meethalfway.adapters.web.dto.MeetingResponse;
import app.meethalfway.adapters.web.dto.RecommendationResponse;
import app.meethalfway.adapters.web.dto.RoutingFailureResponse;
import app.meethalfway.adapters.web.dto.WebMapper;
import app.meethalfway.application.ComputeRecommendations;
import app.meethalfway.application.CreateMeeting;
import app.meethalfway.application.DeleteMeeting;
import app.meethalfway.application.EditMeeting;
import app.meethalfway.application.GetMeeting;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.AddressSuggestion;
import app.meethalfway.domain.port.GeocodingProvider;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for meetings and geocoding (Requirements 1, 6, 7, 8, 9,
 * 11). Exposes the versioned surface under {@code /api/v1} with kebab-case path
 * segments and camelCase JSON. It delegates to the framework-free application
 * use cases and never contains business logic itself; all inputs are validated
 * at the boundary and mapped through {@link WebMapper}.
 *
 * <p>Dependencies are supplied by constructor injection only (no field
 * injection); the controller depends solely on application use cases, the
 * domain {@link GeocodingProvider} port, the domain {@link EngineConfig}, and the
 * web mapper &mdash; never on the {@code config} layer.
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingController {

    private final CreateMeeting createMeeting;
    private final GetMeeting getMeeting;
    private final EditMeeting editMeeting;
    private final DeleteMeeting deleteMeeting;
    private final ComputeRecommendations computeRecommendations;
    private final GeocodingProvider geocodingProvider;
    private final EngineConfig engineConfig;
    private final WebMapper webMapper;

    /**
     * @param createMeeting          create use case; must not be null
     * @param getMeeting             get use case; must not be null
     * @param editMeeting            edit use case; must not be null
     * @param deleteMeeting          delete use case; must not be null
     * @param computeRecommendations compute use case; must not be null
     * @param geocodingProvider      geocoding port for autocomplete; must not be null
     * @param engineConfig           engine configuration for city-agnostic scoping;
     *                               must not be null
     * @param webMapper              DTO/domain mapper; must not be null
     */
    public MeetingController(
            CreateMeeting createMeeting,
            GetMeeting getMeeting,
            EditMeeting editMeeting,
            DeleteMeeting deleteMeeting,
            ComputeRecommendations computeRecommendations,
            GeocodingProvider geocodingProvider,
            EngineConfig engineConfig,
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
        if (geocodingProvider == null) {
            throw new IllegalArgumentException("geocodingProvider must not be null");
        }
        if (engineConfig == null) {
            throw new IllegalArgumentException("engineConfig must not be null");
        }
        if (webMapper == null) {
            throw new IllegalArgumentException("webMapper must not be null");
        }
        this.createMeeting = createMeeting;
        this.getMeeting = getMeeting;
        this.editMeeting = editMeeting;
        this.deleteMeeting = deleteMeeting;
        this.computeRecommendations = computeRecommendations;
        this.geocodingProvider = geocodingProvider;
        this.engineConfig = engineConfig;
        this.webMapper = webMapper;
    }

    /**
     * Creates a meeting (Requirement 1, 9.1). Returns 201 with the created
     * meeting.
     *
     * @param request the validated create request
     * @return 201 Created with the meeting body
     */
    @PostMapping("/meetings")
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
    @GetMapping("/meetings/{code}")
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
    @PutMapping("/meetings/{code}")
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
    @DeleteMapping("/meetings/{code}")
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
    @PostMapping("/meetings/{code}/recommendations")
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

    /**
     * Proposes address autocomplete suggestions as the Creator types
     * (Requirement 9.7). Returns 200 with the suggestions; never exposes provider
     * keys. City-agnostic scoping is delegated to the provider via the injected
     * {@link EngineConfig}.
     *
     * @param query the partial address text (query parameter {@code q})
     * @return 200 with the list of suggestions
     */
    @GetMapping("/geocode/autocomplete")
    public ResponseEntity<List<AddressSuggestionResponse>> autocomplete(
            @RequestParam("q") String query) {
        List<AddressSuggestion> suggestions = geocodingProvider.autocomplete(query, engineConfig);
        return ResponseEntity.ok(webMapper.toSuggestionResponses(suggestions));
    }
}
