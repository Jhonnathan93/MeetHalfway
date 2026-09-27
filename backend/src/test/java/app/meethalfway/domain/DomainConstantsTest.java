package app.meethalfway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies the fixed, non-configurable efficiency tolerance (Requirement 4.3). */
class DomainConstantsTest {

    @Test
    void efficiencyToleranceIsFifteenPercent() {
        assertThat(DomainConstants.EFFICIENCY_TOLERANCE).isEqualTo(0.15);
    }
}
