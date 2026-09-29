package app.meethalfway.meetings.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariants for the configured outlier rule (Requirement 7.1). */
class OutlierRuleTest {

    @Test
    void medianMultipleAcceptsAPositiveK() {
        OutlierRule rule = new OutlierRule(2.0);
        assertThat(rule.k()).isEqualTo(2.0);
    }

    @Test
    void medianMultipleRejectsZeroK() {
        assertThatThrownBy(() -> new OutlierRule(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void medianMultipleRejectsNegativeK() {
        assertThatThrownBy(() -> new OutlierRule(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void medianMultipleRejectsNonFiniteK() {
        assertThatThrownBy(() -> new OutlierRule(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

}
