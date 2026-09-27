package app.meethalfway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UrlCodeGenerator} (Task 9.2): codes are the expected
 * length, use only the unambiguous alphabet, and collisions are avoided via the
 * injected existence predicate.
 */
class UrlCodeGeneratorTest {

    private final UrlCodeGenerator generator = new UrlCodeGenerator();

    @Test
    void generatesEightCharacterCodesFromTheUnambiguousAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = generator.generate(existing -> false);
            assertThat(code).hasSize(8);
            assertThat(code).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}");
        }
    }

    @Test
    void skipsCodesThatAlreadyExist() {
        Set<String> taken = new HashSet<>();
        // Reject the first generated code once, forcing at least one retry.
        boolean[] firstRejected = {false};
        String code = generator.generate(candidate -> {
            if (!firstRejected[0]) {
                firstRejected[0] = true;
                taken.add(candidate);
                return true;
            }
            return taken.contains(candidate);
        });
        assertThat(taken).doesNotContain(code);
    }

    @Test
    void rejectsANullPredicate() {
        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
