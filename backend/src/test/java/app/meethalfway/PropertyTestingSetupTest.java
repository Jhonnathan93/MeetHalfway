package app.meethalfway;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Smoke test proving the jqwik property-based testing engine is on the test
 * classpath and executing. Real engine properties (Properties 1-17) are added
 * in later tasks; this only verifies the toolchain wiring.
 */
class PropertyTestingSetupTest {

    @Property(tries = 100)
    void additionIsCommutative(@ForAll @IntRange(min = -1000, max = 1000) int a,
                               @ForAll @IntRange(min = -1000, max = 1000) int b) {
        assertThat(a + b).isEqualTo(b + a);
    }
}
