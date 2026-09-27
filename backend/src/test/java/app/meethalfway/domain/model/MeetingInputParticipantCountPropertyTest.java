package app.meethalfway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the participant-count validation invariant of
 * {@link MeetingInput}.
 *
 * <p>Feature: meeting-recommendation-engine, Property 1: Participant-count
 * validation invariant &mdash; the engine accepts a meeting only if its
 * participant count is between {@value MeetingInput#MIN_PARTICIPANTS} and
 * {@value MeetingInput#MAX_PARTICIPANTS} inclusive; any count below the minimum
 * or above the maximum is rejected with a validation error that identifies the
 * violated bound.
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3
 */
class MeetingInputParticipantCountPropertyTest {

    private static ParticipantInput participant(int index) {
        return new ParticipantInput(
                new ParticipantId("p" + index),
                "Participant " + index,
                new Coordinate(6.0 + index * 0.001, -75.0 - index * 0.001));
    }

    private static List<ParticipantInput> participants(int count) {
        List<ParticipantInput> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(participant(i));
        }
        return list;
    }

    /**
     * Property 1 &mdash; acceptance side: every count within the inclusive
     * [MIN_PARTICIPANTS, MAX_PARTICIPANTS] range yields a valid MeetingInput
     * preserving that exact count. (Requirement 1.1)
     */
    @Property(tries = 100)
    void acceptsAnyCountWithinInclusiveBounds(
            @ForAll @IntRange(min = MeetingInput.MIN_PARTICIPANTS, max = MeetingInput.MAX_PARTICIPANTS)
                    int count) {
        MeetingInput input = new MeetingInput(participants(count), TransportMode.DRIVING);

        assertThat(input.participants()).hasSize(count);
    }

    /**
     * Property 1 &mdash; lower-bound rejection: any count below the minimum is
     * rejected with an error naming the minimum bound. (Requirement 1.2)
     */
    @Property(tries = 100)
    void rejectsAnyCountBelowMinimumIdentifyingTheBound(
            @ForAll @IntRange(min = 0, max = MeetingInput.MIN_PARTICIPANTS - 1) int count) {
        List<ParticipantInput> tooFew = participants(count);

        assertThatThrownBy(() -> new MeetingInput(tooFew, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(MeetingInput.MIN_PARTICIPANTS));
    }

    /**
     * Property 1 &mdash; upper-bound rejection: any count above the maximum is
     * rejected with an error naming the maximum bound. (Requirement 1.3)
     */
    @Property(tries = 100)
    void rejectsAnyCountAboveMaximumIdentifyingTheBound(
            @ForAll @IntRange(min = MeetingInput.MAX_PARTICIPANTS + 1, max = 50) int count) {
        List<ParticipantInput> tooMany = participants(count);

        assertThatThrownBy(() -> new MeetingInput(tooMany, TransportMode.WALKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(MeetingInput.MAX_PARTICIPANTS));
    }
}
