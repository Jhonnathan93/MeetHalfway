package app.meethalfway.domain.model;

/**
 * A whole-minute travel time (Requirement 8.2).
 *
 * <p>Invariant: the value is a non-negative integer number of minutes. The
 * compact constructor rejects negative values so a travel time can never be
 * negative.
 *
 * @param value non-negative whole number of minutes
 */
public record Minutes(int value) {

    public Minutes {
        if (value < 0) {
            throw new IllegalArgumentException("minutes must be non-negative, was: " + value);
        }
    }
}
