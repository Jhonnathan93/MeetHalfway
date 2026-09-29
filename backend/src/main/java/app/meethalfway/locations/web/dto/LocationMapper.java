package app.meethalfway.locations.web.dto;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.locations.domain.port.AddressSuggestion;
import app.meethalfway.shared.web.dto.CoordinateResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Explicit, stateless mapper between geocoding domain types and the locations
 * module's web DTOs (Requirement 11.2). Domain records are never serialized
 * directly: this named mapper spells out every conversion so the wire contract
 * stays flat and camelCase.
 *
 * <p>This mapping was extracted from the shared {@code WebMapper} when the
 * geocoding endpoints moved to {@link app.meethalfway.locations.web.LocationController}
 * (R2.5). It is framework-free and holds no state, so it is safe to share as a
 * singleton bean.
 */
@Component
public final class LocationMapper {

    /**
     * Maps geocoding suggestions to their wire representation. No provider key is
     * ever carried on a suggestion.
     *
     * @param suggestions the domain suggestions; must not be null
     * @return the suggestion responses
     */
    public List<AddressSuggestionResponse> toSuggestionResponses(List<AddressSuggestion> suggestions) {
        if (suggestions == null) {
            throw new IllegalArgumentException("suggestions must not be null");
        }
        List<AddressSuggestionResponse> result = new ArrayList<>(suggestions.size());
        suggestions.forEach(suggestion ->
                result.add(new AddressSuggestionResponse(suggestion.description(), suggestion.placeId())));
        return result;
    }

    /**
     * Maps a domain {@link Coordinate} to its wire representation so the resolve
     * endpoint can return a resolved location directly.
     *
     * @param coordinate the domain coordinate; must not be null
     * @return the coordinate response
     */
    public CoordinateResponse toCoordinateResponse(Coordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("coordinate must not be null");
        }
        return new CoordinateResponse(coordinate.lat(), coordinate.lng());
    }
}
