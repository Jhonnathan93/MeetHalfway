package app.meethalfway.locations.domain.port;

/**
 * A single autocomplete suggestion returned by a {@link GeocodingProvider} while
 * a Creator types an address (Requirement 9.7).
 *
 * <p>The suggestion pairs a human-readable label (shown in the search box) with
 * an opaque, provider-scoped {@code placeId} the frontend can hand back to
 * {@link GeocodingProvider#resolve(String)} to obtain a precise coordinate. No
 * coordinate is carried here because autocomplete only proposes candidates; the
 * exact location is resolved in a separate step.
 *
 * @param description the human-readable address label to display
 * @param placeId     an opaque, non-blank provider reference used to resolve the
 *                    suggestion to a coordinate
 */
public record AddressSuggestion(String description, String placeId) {

    public AddressSuggestion {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must be a non-blank label");
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId must be a non-blank provider reference");
        }
    }
}
