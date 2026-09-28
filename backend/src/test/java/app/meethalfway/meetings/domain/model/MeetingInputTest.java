package app.meethalfway.meetings.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.shared.domain.TransportMode;

/**
 * Construction-time invariant checks for {@link MeetingInput}
 * (Requirements 1.1&ndash;1.4).
 */
class MeetingInputTest {

    private static ParticipantInput participant(int index) {
        return new ParticipantInput(
                new ParticipantId("p" + index),
                "Participant " + index,
                new Coordinate(6.0 + index * 0.01, -75.0 - index * 0.01));
    }

    private static List<ParticipantInput> participants(int count) {
        List<ParticipantInput> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(participant(i));
        }
        return list;
    }

    @Test
    void acceptsMinimumParticipantCount() {
        MeetingInput input = new MeetingInput(participants(2), TransportMode.DRIVING);

        assertThat(input.participants()).hasSize(2);
        assertThat(input.mode()).isEqualTo(TransportMode.DRIVING);
    }

    @Test
    void acceptsMaximumParticipantCount() {
        assertThat(new MeetingInput(participants(10), TransportMode.WALKING).participants())
                .hasSize(10);
    }

    @Test
    void rejectsFewerThanTwoParticipants() {
        List<ParticipantInput> one = participants(1);

        assertThatThrownBy(() -> new MeetingInput(one, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(MeetingInput.MIN_PARTICIPANTS));
    }

    @Test
    void rejectsMoreThanTenParticipants() {
        List<ParticipantInput> eleven = participants(11);

        assertThatThrownBy(() -> new MeetingInput(eleven, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(MeetingInput.MAX_PARTICIPANTS));
    }

    @Test
    void rejectsNullMode() {
        List<ParticipantInput> two = participants(2);

        assertThatThrownBy(() -> new MeetingInput(two, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullParticipantList() {
        assertThatThrownBy(() -> new MeetingInput(null, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsListContainingNullEntry() {
        List<ParticipantInput> withNull = participants(2);
        withNull.add(null);

        assertThatThrownBy(() -> new MeetingInput(withNull, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participantsListIsImmutable() {
        MeetingInput input = new MeetingInput(participants(2), TransportMode.DRIVING);

        assertThatThrownBy(() -> input.participants().add(participant(99)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
