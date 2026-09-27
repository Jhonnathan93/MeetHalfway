package app.meethalfway.domain.model;

/**
 * The single travel method shared by all participants in a meeting
 * (Requirement 1.4). The MVP supports exactly two modes; mixed modes are out of
 * scope.
 */
public enum TransportMode {
    DRIVING,
    WALKING;

    /**
     * The external, wire-level names accepted for a transport mode
     * (Requirement 1.4): exactly {@code "driving"} and {@code "walking"}.
     */
    public static final String DRIVING_VALUE = "driving";
    public static final String WALKING_VALUE = "walking";

    /**
     * Parses an external transport-mode value into a {@link TransportMode}
     * (Requirements 1.4, 1.5).
     *
     * <p>Accepts only the exact supported values {@value #DRIVING_VALUE} and
     * {@value #WALKING_VALUE}. Any other value&mdash;including {@code null},
     * blank strings, or unsupported words&mdash;is rejected with an
     * {@link IllegalArgumentException} whose message names the supported modes so
     * the web adapter can surface an actionable validation error.
     *
     * @param value the external transport-mode value
     * @return the matching {@link TransportMode}
     * @throws IllegalArgumentException if {@code value} is not exactly one of the
     *                                  supported modes
     */
    public static TransportMode fromValue(String value) {
        if (DRIVING_VALUE.equals(value)) {
            return DRIVING;
        }
        if (WALKING_VALUE.equals(value)) {
            return WALKING;
        }
        throw new IllegalArgumentException(
                "unsupported transport mode: " + describe(value)
                        + "; supported modes are \"" + DRIVING_VALUE + "\" and \""
                        + WALKING_VALUE + "\"");
    }

    private static String describe(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
