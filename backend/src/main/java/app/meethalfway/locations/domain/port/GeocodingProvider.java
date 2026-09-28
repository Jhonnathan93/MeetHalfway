package app.meethalfway.locations.domain.port;

import app.meethalfway.meetings.domain.model.EngineConfig;
import java.util.List;

/**
 * Outbound port for turning addresses into coordinates (Requirement 9.7). The
 * core depends only on this interface; the concrete geocoding service lives in
 * the {@code adapters} layer and is injected explicitly, keeping provider API
 * keys backend-only (Requirement 11.4) and the provider swappable without core
 * changes.
 *
 * <p>Two operations back the address search box:
 * <ul>
 *   <li>{@link #autocomplete(String, EngineConfig)} proposes address suggestions
 *       as the Creator types; {@code EngineConfig} lets the adapter bias or bound
 *       suggestions with city-agnostic configuration (e.g. service bounds)
 *       rather than a hard-coded region.</li>
 *   <li>{@link #resolve(String)} turns a chosen address (or {@code placeId})
 *       into a precise coordinate.</li>
 * </ul>
 */
public interface GeocodingProvider {

    /**
     * Proposes address suggestions for a partial query as the Creator types.
     *
     * @param query  the partial address text entered so far
     * @param config the engine configuration, providing city-agnostic bounds the
     *               adapter may use to scope suggestions
     * @return the suggestions to display; empty when there is no match. Never
     *         {@code null}.
     */
    List<AddressSuggestion> autocomplete(String query, EngineConfig config);

    /**
     * Resolves an address (or a suggestion reference) to a precise location.
     *
     * @param address the address text or provider place reference to resolve
     * @return a {@link GeocodeResult.Resolved} with the coordinate, or a
     *         {@link GeocodeResult.NotFound} carrying the reason it could not be
     *         resolved
     */
    GeocodeResult resolve(String address);
}
