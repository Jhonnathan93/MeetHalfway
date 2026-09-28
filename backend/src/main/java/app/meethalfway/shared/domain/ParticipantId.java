package app.meethalfway.shared.domain;

/**
 * Stable identifier for a participant within a meeting.
 *
 * <p>Invariant: the value must be present and non-blank so participants remain
 * distinguishable in travel-time maps and outlier sets. The identifier is
 * framework-free and carries no persistence semantics.
 *
 * @param value non-blank participant identifier
 */
public record ParticipantId(String value) {

    public ParticipantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("participant id must not be null or blank");
        }
    }
}
