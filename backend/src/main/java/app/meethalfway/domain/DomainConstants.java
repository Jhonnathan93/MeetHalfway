package app.meethalfway.domain;

/**
 * Fixed domain constants that are, by requirement, <em>not</em> configurable.
 *
 * <p>{@link #EFFICIENCY_TOLERANCE} defines the Fairest strategy's feasible set:
 * a candidate is feasible when {@code sumTime <= (1 + EFFICIENCY_TOLERANCE) * T*}
 * (Requirements 4.2, 4.3). Requirement 4.3 mandates this 15% allowance be a fixed
 * constant, so it lives here in the framework-free domain and is deliberately
 * absent from {@code EngineConfig}, which holds only city-specific/tunable
 * values.
 */
public final class DomainConstants {

    /** The fixed 15% efficiency allowance for the Fairest strategy (Requirement 4.3). */
    public static final double EFFICIENCY_TOLERANCE = 0.15;

    private DomainConstants() {
        // Constants holder; not instantiable.
    }
}
