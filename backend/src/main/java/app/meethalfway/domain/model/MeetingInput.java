package app.meethalfway.domain.model;

import java.util.List;

/**
 * The validated input to a single recommendation computation: the set of
 * participants and the shared transport mode.
 *
 * <p>Invariants (Requirements 1.1&ndash;1.4): the participant count must be
 * between {@value #MIN_PARTICIPANTS} and {@value #MAX_PARTICIPANTS} inclusive,
 * the mode must be present, and no participant may be {@code null}. The compact
 * constructor re-validates these so an invalid {@code MeetingInput} can never be
 * built. When a bound is violated the error message names the specific bound so
 * the web adapter can surface an actionable validation error.
 *
 * <p>The participant list is defensively copied into an unmodifiable list to keep
 * the value object immutable.
 *
 * @param participants the meeting participants; size in
 *                     {@code [MIN_PARTICIPANTS, MAX_PARTICIPANTS]}
 * @param mode         the single transport mode shared by all participants
 */
public record MeetingInput(List<ParticipantInput> participants, TransportMode mode) {

    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 10;

    public MeetingInput {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("transport mode must not be null");
        }
        int count = participants.size();
        if (count < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "a meeting requires at least " + MIN_PARTICIPANTS
                            + " participants, but had: " + count);
        }
        if (count > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "a meeting allows at most " + MAX_PARTICIPANTS
                            + " participants, but had: " + count);
        }
        if (participants.contains(null)) {
            throw new IllegalArgumentException("participants must not contain a null entry");
        }
        participants = List.copyOf(participants);
    }
}
