package app.meethalfway.domain.testing;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.port.AddressSuggestion;
import app.meethalfway.domain.port.GeocodeResult;
import app.meethalfway.domain.port.GeocodingProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, in-memory {@link GeocodingProvider} for tests.
 *
 * <p>Backs the address-search box (autocomplete + resolve) without any network
 * call, so wiring and engine tests that need to turn an address into a
 * {@link Coordinate} stay offline and reproducible.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li><b>autocomplete</b> — returns, in insertion order, every registered
 *       {@link AddressSuggestion} whose description contains the query text
 *       (case-insensitive). A blank query yields an empty list. When
 *       {@link Builder#restrictAutocompleteToServiceBounds()} is enabled, a
 *       suggestion is only proposed when its registered resolve coordinate lies
 *       within the {@link EngineConfig} service bounds, exercising the port's
 *       city-agnostic scoping contract.</li>
 *   <li><b>resolve</b> — returns the registered {@link GeocodeResult} for the
 *       given address/placeId (matched case-insensitively). Unregistered inputs
 *       yield a {@link GeocodeResult.NotFound} so callers exercise the
 *       unresolved path deterministically.</li>
 * </ul>
 *
 * <p>Instances are immutable after {@link Builder#build()}.
 */
public final class FakeGeocodingProvider implements GeocodingProvider {

    private final List<AddressSuggestion> suggestions;
    /** Keyed by lower-cased description and placeId so either resolves. */
    private final Map<String, GeocodeResult> resolutions;
    /** Resolve coordinate per suggestion placeId, for service-bounds scoping. */
    private final Map<String, Coordinate> suggestionCoordinates;
    private final boolean restrictToBounds;
    private final String notFoundReason;

    private FakeGeocodingProvider(Builder builder) {
        this.suggestions = List.copyOf(builder.suggestions);
        this.resolutions = Map.copyOf(builder.resolutions);
        this.suggestionCoordinates = Map.copyOf(builder.suggestionCoordinates);
        this.restrictToBounds = builder.restrictToBounds;
        this.notFoundReason = builder.notFoundReason;
    }

    /**
     * @return an empty fake that proposes no suggestions and resolves nothing
     */
    public static FakeGeocodingProvider empty() {
        return builder().build();
    }

    /**
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<AddressSuggestion> autocomplete(String query, EngineConfig config) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (restrictToBounds && config == null) {
            throw new IllegalArgumentException(
                    "config must not be null when autocomplete is bounded by service bounds");
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<AddressSuggestion> matches = new ArrayList<>();
        for (AddressSuggestion suggestion : suggestions) {
            if (!suggestion.description().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (restrictToBounds && !withinBounds(suggestion, config)) {
                continue;
            }
            matches.add(suggestion);
        }
        return List.copyOf(matches);
    }

    @Override
    public GeocodeResult resolve(String address) {
        if (address == null || address.isBlank()) {
            return GeocodeResult.notFound(notFoundReason);
        }
        GeocodeResult result = resolutions.get(address.toLowerCase(Locale.ROOT));
        return result != null ? result : GeocodeResult.notFound(notFoundReason);
    }

    private boolean withinBounds(AddressSuggestion suggestion, EngineConfig config) {
        Coordinate coordinate = suggestionCoordinates.get(suggestion.placeId());
        return coordinate != null && config.serviceBounds().contains(coordinate);
    }

    /** Builder for {@link FakeGeocodingProvider}. */
    public static final class Builder {
        private final List<AddressSuggestion> suggestions = new ArrayList<>();
        private final Map<String, GeocodeResult> resolutions = new LinkedHashMap<>();
        private final Map<String, Coordinate> suggestionCoordinates = new LinkedHashMap<>();
        private boolean restrictToBounds = false;
        private String notFoundReason = "address could not be resolved";

        private Builder() {
        }

        /**
         * Registers an address that both appears in autocomplete (matching its
         * description) and resolves to the given coordinate. This is the common
         * case: one call wires up a fully usable address.
         *
         * @param description the human-readable label (also the resolve key)
         * @param placeId     the opaque provider reference (also a resolve key)
         * @param coordinate  the coordinate the address resolves to
         * @return this builder
         */
        public Builder withAddress(String description, String placeId, Coordinate coordinate) {
            if (coordinate == null) {
                throw new IllegalArgumentException("coordinate must not be null");
            }
            AddressSuggestion suggestion = new AddressSuggestion(description, placeId);
            suggestions.add(suggestion);
            suggestionCoordinates.put(placeId, coordinate);
            GeocodeResult resolved = GeocodeResult.resolved(coordinate);
            resolutions.put(description.toLowerCase(Locale.ROOT), resolved);
            resolutions.put(placeId.toLowerCase(Locale.ROOT), resolved);
            return this;
        }

        /**
         * Registers an autocomplete suggestion without a resolve mapping. Useful
         * for testing that a suggestion the provider proposes may still fail to
         * resolve.
         *
         * @param description the label to match on
         * @param placeId     the opaque provider reference
         * @return this builder
         */
        public Builder withSuggestion(String description, String placeId) {
            suggestions.add(new AddressSuggestion(description, placeId));
            return this;
        }

        /**
         * Registers an address that fails to resolve with a specific reason,
         * exercising the {@link GeocodeResult.NotFound} path.
         *
         * @param address the address/placeId that will not resolve
         * @param reason  the non-blank failure reason
         * @return this builder
         */
        public Builder withUnresolvable(String address, String reason) {
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("address must be non-blank");
            }
            resolutions.put(address.toLowerCase(Locale.ROOT), GeocodeResult.notFound(reason));
            return this;
        }

        /**
         * Restricts autocomplete to suggestions whose resolve coordinate lies
         * within the {@link EngineConfig} service bounds, exercising the port's
         * city-agnostic scoping contract.
         *
         * @return this builder
         */
        public Builder restrictAutocompleteToServiceBounds() {
            this.restrictToBounds = true;
            return this;
        }

        /**
         * Overrides the reason returned for unregistered/blank resolve inputs.
         *
         * @param reason the non-blank default not-found reason
         * @return this builder
         */
        public Builder withDefaultNotFoundReason(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must be non-blank");
            }
            this.notFoundReason = reason;
            return this;
        }

        /**
         * @return an immutable {@link FakeGeocodingProvider}
         */
        public FakeGeocodingProvider build() {
            return new FakeGeocodingProvider(this);
        }
    }
}
