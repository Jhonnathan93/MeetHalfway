package app.meethalfway.adapters.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NameSanitizer} (Task 12.1). Confirms the sanitize-first
 * boundary rule: null becomes empty, whitespace is trimmed, control and basic
 * injection characters are stripped, and the result is length-capped.
 */
class NameSanitizerTest {

    @Test
    void nullBecomesEmpty() {
        assertThat(NameSanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(NameSanitizer.sanitize("  Ana  ")).isEqualTo("Ana");
    }

    @Test
    void stripsControlCharacters() {
        assertThat(NameSanitizer.sanitize("An\u0000a\tB\nC")).isEqualTo("AnaBC");
    }

    @Test
    void stripsBasicInjectionCharacters() {
        assertThat(NameSanitizer.sanitize("<script>alert('x')</script>"))
                .isEqualTo("scriptalert(x)/script");
    }

    @Test
    void keepsAccentsAndUnicodeLetters() {
        assertThat(NameSanitizer.sanitize("José Muñoz")).isEqualTo("José Muñoz");
    }

    @Test
    void capsLengthAtMax() {
        String raw = "x".repeat(NameSanitizer.MAX_LENGTH + 50);
        assertThat(NameSanitizer.sanitize(raw)).hasSize(NameSanitizer.MAX_LENGTH);
    }
}
