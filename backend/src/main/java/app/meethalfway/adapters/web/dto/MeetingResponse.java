package app.meethalfway.adapters.web.dto;

import java.util.List;

/**
 * Wire representation of a meeting (Requirement 9). Returned by create, get, and
 * edit.
 *
 * <p>Carries the access code, the participants, the transport mode wire value
 * ({@code driving}/{@code walking}), and any stored recommendation
 * ({@code recommendation} is {@code null} until a computation has been persisted).
 * The domain aggregate is mapped explicitly rather than serialized directly, so
 * the JSON contract stays camelCase and free of {@code Optional} wrappers.
 *
 * @param urlCode        the short access code granting view/edit access
 * @param participants   the meeting participants
 * @param transportMode  the shared transport mode wire value
 * @param recommendation the stored recommendation, or {@code null} when none
 */
public record MeetingResponse(
        String urlCode,
        List<ParticipantResponse> participants,
        String transportMode,
        RecommendationResponse recommendation) {
}
