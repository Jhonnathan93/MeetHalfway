package app.meethalfway.meetings.domain.model;

import java.util.Optional;

/**
 * A persisted meeting: the aggregate the {@code MeetingRepository} port stores
 * and retrieves (Requirement 9). It pairs the {@code urlCode} that grants
 * unauthenticated access (Requirement 9.1, 9.2) with the {@link MeetingInput}
 * (participants and shared transport mode) and any {@link RecommendationOutcome}
 * already computed and stored for it (Requirement 9.3).
 *
 * <p>This is a framework-free domain aggregate; it carries no persistence
 * annotations. The persistence adapter maps between this and its JPA entities,
 * keeping the domain independent of the database (Clean Architecture dependency
 * rule).
 *
 * <p>Access is by {@code urlCode} alone — there is no user identity anywhere on
 * the aggregate (Requirement 9.2).
 *
 * @param urlCode        the short, URL-safe code that grants view/edit access;
 *                       must be non-blank
 * @param input          the participants and shared transport mode
 * @param recommendation the stored recommendation outcome, present only once a
 *                       computation has been run and persisted; otherwise empty
 */
public record Meeting(
        String urlCode,
        MeetingInput input,
        Optional<RecommendationOutcome.Success> recommendation) {

    public Meeting {
        if (urlCode == null || urlCode.isBlank()) {
            throw new IllegalArgumentException("urlCode must not be null or blank");
        }
        if (input == null) {
            throw new IllegalArgumentException("meeting input must not be null");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException(
                    "recommendation must not be null; use Optional.empty() when none is stored");
        }
    }

    /**
     * Convenience factory for a meeting that has no stored recommendation yet.
     *
     * @param urlCode the access code
     * @param input   the participants and transport mode
     * @return a {@code Meeting} whose {@code recommendation} is empty
     */
    public static Meeting of(String urlCode, MeetingInput input) {
        return new Meeting(urlCode, input, Optional.empty());
    }
}
