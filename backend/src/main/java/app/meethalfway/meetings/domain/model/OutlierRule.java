package app.meethalfway.meetings.domain.model;

/** Flags a participant whose travel time is greater than {@code k * median}. */
public record OutlierRule(double k) {

    public OutlierRule {
        if (!Double.isFinite(k) || k <= 0.0) {
            throw new IllegalArgumentException("k must be finite and strictly positive, was: " + k);
        }
    }
}
