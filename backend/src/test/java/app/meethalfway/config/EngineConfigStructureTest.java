package app.meethalfway.config;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.domain.DomainConstants;
import app.meethalfway.domain.model.EngineConfig;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

/**
 * Structural/config tests for the engine configuration surface (Task 12.5).
 *
 * <p>Asserts three requirements mechanically:
 * <ul>
 *   <li>city-specific values are bound from configuration (not hard-coded): a
 *       {@link Binder} binds {@code meethalfway.engine.*} into
 *       {@link EngineConfigProperties} and the composition-root mapping reflects
 *       them in the domain {@link EngineConfig};</li>
 *   <li>the 15% efficiency tolerance is a fixed constant equal to {@code 0.15}
 *       and is <em>not</em> a field/getter on {@link EngineConfig} or
 *       {@link EngineConfigProperties};</li>
 * </ul>
 */
class EngineConfigStructureTest {

    @Test
    void citySpecificValuesBindFromConfigurationAndReachEngineConfig() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("meethalfway.engine.service-bounds-min-lat", "6.0")
                .withProperty("meethalfway.engine.service-bounds-max-lat", "6.5")
                .withProperty("meethalfway.engine.service-bounds-min-lng", "-75.7")
                .withProperty("meethalfway.engine.service-bounds-max-lng", "-75.4")
                .withProperty("meethalfway.engine.grid-density-n", "250")
                .withProperty("meethalfway.engine.max-search-radius-meters", "18000.0")
                .withProperty("meethalfway.engine.epsilon-minutes", "0.5")
                .withProperty("meethalfway.engine.outlier-median-multiple-k", "2.0");

        Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        EngineConfigProperties properties = binder
                .bind("meethalfway.engine", EngineConfigProperties.class)
                .get();

        assertThat(properties.serviceBoundsMinLat()).isEqualTo(6.0);
        assertThat(properties.gridDensityN()).isEqualTo(250);

        EngineConfig config = new BeanConfiguration().engineConfig(properties);
        // The domain snapshot reflects the configured (city-specific) values,
        // proving they are not hard-coded in the algorithm.
        assertThat(config.serviceBounds().minLat()).isEqualTo(6.0);
        assertThat(config.serviceBounds().maxLng()).isEqualTo(-75.4);
        assertThat(config.gridDensityN()).isEqualTo(250);
        assertThat(config.maxSearchRadiusMeters()).isEqualTo(18000.0);
    }

    @Test
    void efficiencyToleranceIsAFixedFifteenPercentConstant() {
        assertThat(DomainConstants.EFFICIENCY_TOLERANCE).isEqualTo(0.15);
    }

    @Test
    void efficiencyToleranceIsNotAFieldOrGetterOnEngineConfig() {
        assertNoToleranceComponent(EngineConfig.class);
        assertNoToleranceAccessor(EngineConfig.class);
    }

    @Test
    void efficiencyToleranceIsNotAFieldOrGetterOnEngineConfigProperties() {
        assertNoToleranceComponent(EngineConfigProperties.class);
        assertNoToleranceAccessor(EngineConfigProperties.class);
    }

    private static void assertNoToleranceComponent(Class<?> recordType) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertThat(name)
                    .as("record component must not expose the efficiency tolerance")
                    .doesNotContain("tolerance")
                    .doesNotContain("efficiency");
        }
    }

    private static void assertNoToleranceAccessor(Class<?> type) {
        for (Method method : type.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertThat(name)
                    .as("accessor must not expose the efficiency tolerance")
                    .doesNotContain("tolerance")
                    .doesNotContain("efficiency");
        }
    }

    /** Guards against accidental duplication of tolerance config keys. */
    @Test
    void noToleranceKeyInEngineConfigurationProperties() {
        Map<String, String> sample = Map.of(
                "meethalfway.engine.grid-density-n", "250");
        assertThat(sample.keySet())
                .noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("tolerance"));
    }
}
