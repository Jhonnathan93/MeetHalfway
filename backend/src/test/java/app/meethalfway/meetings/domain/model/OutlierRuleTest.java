package app.meethalfway.meetings.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariants for the {@link OutlierRule} variants (Requirement 7.1). */
class OutlierRuleTest {

    @Test
    void medianMultipleAcceptsAPositiveK() {
        OutlierRule.MedianMultiple rule = new OutlierRule.MedianMultiple(2.0);
        assertThat(rule.k()).isEqualTo(2.0);
        assertThat((OutlierRule) rule).isInstanceOf(OutlierRule.class);
    }

    @Test
    void medianMultipleRejectsZeroK() {
        assertThatThrownBy(() -> new OutlierRule.MedianMultiple(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void medianMultipleRejectsNegativeK() {
        assertThatThrownBy(() -> new OutlierRule.MedianMultiple(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void medianMultipleRejectsNonFiniteK() {
        assertThatThrownBy(() -> new OutlierRule.MedianMultiple(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void percentileAcceptsP90() {
        OutlierRule.Percentile rule = new OutlierRule.Percentile(90.0);
        assertThat(rule.p()).isEqualTo(90.0);
        assertThat((OutlierRule) rule).isInstanceOf(OutlierRule.class);
    }

    @Test
    void percentileAcceptsTheUpperBound() {
        assertThat(new OutlierRule.Percentile(100.0).p()).isEqualTo(100.0);
    }

    @Test
    void percentileRejectsZero() {
        assertThatThrownBy(() -> new OutlierRule.Percentile(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(0, 100]");
    }

    @Test
    void percentileRejectsAboveOneHundred() {
        assertThatThrownBy(() -> new OutlierRule.Percentile(100.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(0, 100]");
    }

    @Test
    void percentileRejectsNonFinite() {
        assertThatThrownBy(() -> new OutlierRule.Percentile(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }
}
