package app.meethalfway.locations.adapters.places;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.locations.domain.port.Place;
import app.meethalfway.locations.domain.port.PlaceQuery;
import app.meethalfway.locations.domain.port.PlacesProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * No-op {@link PlacesProvider} for the MVP.
 *
 * <p>Places are out of scope for the MVP (Requirement 12.3), but the port is
 * defined so the post-MVP "list establishments in the zone" feature only requires
 * a real adapter here, not changes to the core. This stub satisfies the port
 * contract without any network calls or external dependencies: it validates its
 * arguments (consistent with a real adapter's boundary) and always returns an
 * empty, immutable list.
 *
 * <p>Registered as a Spring bean via {@link Component} so it can be injected
 * wherever a {@code PlacesProvider} is required; wiring elsewhere stays unchanged
 * when the real adapter replaces it.
 */
@Component
public class StubPlacesProvider implements PlacesProvider {

    /**
     * {@inheritDoc}
     *
     * <p>Always returns an empty list. Arguments are validated for null so the
     * stub honors the same contract a real adapter would; no I/O is performed.
     */
    @Override
    public List<Place> nearby(Coordinate center, PlaceQuery query) {
        Objects.requireNonNull(center, "center must not be null");
        Objects.requireNonNull(query, "query must not be null");
        return List.of();
    }
}
