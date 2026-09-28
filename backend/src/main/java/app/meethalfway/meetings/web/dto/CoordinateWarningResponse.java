package app.meethalfway.meetings.web.dto;

/**
 * Wire representation of a single out-of-range coordinate warning
 * (Requirements 10.7, 10.9).
 *
 * <p>A warning surfaces a coordinate that fell outside the valid latitude range
 * {@code [-90, 90]} or longitude range {@code [-180, 180]} without rejecting the
 * whole response: an invalid <em>candidate</em> point is excluded from
 * {@code results} and reported here; an invalid <em>participant origin</em> is
 * reported here rather than silently omitted. This DTO is purely additive to the
 * response contract (Requirements 10.8, 12.4).
 *
 * <p>JSON fields are camelCase.
 *
 * @param kind      the coordinate kind: {@code "CANDIDATE"} or
 *                  {@code "PARTICIPANT_ORIGIN"}
 * @param reference the identifier of the offending coordinate: the strategy
 *                  {@code candidateId} for a candidate, or the participant id for
 *                  a participant origin
 * @param lat       the out-of-range (or paired) latitude in degrees
 * @param lng       the out-of-range (or paired) longitude in degrees
 * @param reason    a human-readable explanation of why the coordinate is invalid
 */
public record CoordinateWarningResponse(
        String kind,
        String reference,
        double lat,
        double lng,
        String reason) {

    /** Warning kind for an out-of-range candidate/meeting point. */
    public static final String KIND_CANDIDATE = "CANDIDATE";

    /** Warning kind for an out-of-range participant origin. */
    public static final String KIND_PARTICIPANT_ORIGIN = "PARTICIPANT_ORIGIN";
}
