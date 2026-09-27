package app.meethalfway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariant checks for {@link ParticipantId}. */
class ParticipantIdTest {

    @Test
    void acceptsNonBlankValue() {
        assertThat(new ParticipantId("p1").value()).isEqualTo("p1");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new ParticipantId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ParticipantId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
