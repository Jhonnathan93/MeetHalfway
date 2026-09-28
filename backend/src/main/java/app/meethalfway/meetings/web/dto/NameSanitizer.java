package app.meethalfway.meetings.web.dto;

/**
 * Explicit, single-purpose sanitizer for participant display names entered at
 * the web boundary (Requirement 11.3 &mdash; validate and sanitize all input).
 *
 * <p>This is a named class with explicit methods rather than an ambiguous
 * {@code *Utils} grab-bag: it exists solely to normalize a raw, user-supplied
 * name into a safe, bounded string before it crosses into the application/domain
 * layer. Sanitization runs <em>before</em> validation, matching the design's
 * "input is sanitized then validated" ordering.
 *
 * <p>The transformation is intentionally conservative and lossless-where-safe:
 * <ul>
 *   <li>{@code null} becomes an empty string (the name is optional; the domain
 *       {@code ParticipantInput} normalizes empty to blank);</li>
 *   <li>leading/trailing whitespace is trimmed;</li>
 *   <li>ISO control characters (including embedded newlines/tabs) are stripped
 *       so a name can never smuggle control bytes into logs or responses;</li>
 *   <li>the raw angle-bracket and quote characters most commonly used for
 *       HTML/script injection ({@code < > " '}) are removed, so a stored name is
 *       inert if later rendered;</li>
 *   <li>the result is capped at {@link #MAX_LENGTH} characters.</li>
 * </ul>
 *
 * <p>No secret or provider key is ever handled here; only participant-supplied
 * display text.
 */
public final class NameSanitizer {

    /** Maximum retained length of a sanitized display name. */
    public static final int MAX_LENGTH = 255;

    private NameSanitizer() {
        // Utility class exposing an explicit static method; not instantiable.
    }

    /**
     * Sanitizes a raw, user-supplied display name into a safe, bounded value.
     *
     * @param raw the raw name as received at the boundary; may be {@code null}
     * @return the sanitized name; never {@code null}, at most {@link #MAX_LENGTH}
     *         characters, free of control and basic injection characters
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        raw.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                return;
            }
            if (codePoint == '<' || codePoint == '>' || codePoint == '"' || codePoint == '\'') {
                return;
            }
            builder.appendCodePoint(codePoint);
        });
        String cleaned = builder.toString().strip();
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).strip();
        }
        return cleaned;
    }
}
