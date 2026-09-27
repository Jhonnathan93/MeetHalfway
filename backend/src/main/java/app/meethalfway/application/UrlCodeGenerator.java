package app.meethalfway.application;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Generates short, URL-safe meeting access codes (Requirement 9.1). Codes are
 * fixed-length strings drawn from an unambiguous alphabet (no {@code 0/O} or
 * {@code 1/l/I}) so they are easy to read and share, and are checked for
 * collisions against an injected predicate before being handed out.
 *
 * <p>This is framework-free application code. The collision check is supplied as
 * a {@link Predicate} so the generator depends on no persistence type; the
 * composition root wires it to the repository's existence check.
 */
public final class UrlCodeGenerator {

    /**
     * Unambiguous Crockford-style alphabet: digits and uppercase letters with the
     * easily-confused characters ({@code 0 O 1 I L}) removed.
     */
    private static final char[] ALPHABET =
            "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    /** Code length: 8 characters over a 31-symbol alphabet (~40 bits of entropy). */
    private static final int CODE_LENGTH = 8;

    /** Bound on collision retries before giving up (defensive; effectively never hit). */
    private static final int MAX_ATTEMPTS = 100;

    private final SecureRandom random;

    /** Creates a generator backed by a {@link SecureRandom}. */
    public UrlCodeGenerator() {
        this.random = new SecureRandom();
    }

    /**
     * Generates a fresh code that does not collide with an existing one.
     *
     * @param exists a predicate returning {@code true} when a code is already in
     *               use; must not be {@code null}
     * @return a new, collision-free URL code
     * @throws IllegalArgumentException if {@code exists} is {@code null}
     * @throws IllegalStateException    if a free code could not be found within
     *                                  the retry bound
     */
    public String generate(Predicate<String> exists) {
        if (exists == null) {
            throw new IllegalArgumentException("exists predicate must not be null");
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not generate a collision-free url code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
