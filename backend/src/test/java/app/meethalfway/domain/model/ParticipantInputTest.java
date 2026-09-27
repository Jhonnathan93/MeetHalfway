package app.meethalfway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariant checks for {@link ParticipantInput}. */
class ParticipantInputTest {

    private static final Coordinate ANY = new Coordinate(6.25, -75.56);

    @Test
    void acceptsFullyPopulatedParticipant() {
        ParticipantInput input = new ParticipantInput(new ParticipantId("p1"), "Alice", ANY);

        assertThat(input.name()).isEqualTo("Alice");
        assertThat(input.location()).isEqualTo(ANY);
    }

    @Test
    void normalizesNullNameToEmptyString() {
        ParticipantInput input = new ParticipantInput(new ParticipantId("p1"), null, ANY);

        assertThat(input.name()).isEmpty();
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new ParticipantInput(null, "Alice", ANY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullLocation() {
        assertThatThrownBy(() -> new ParticipantInput(new ParticipantId("p1"), "Alice", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
