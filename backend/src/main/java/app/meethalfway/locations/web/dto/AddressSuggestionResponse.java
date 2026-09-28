package app.meethalfway.locations.web.dto;

/**
 * Wire representation of a single geocoding autocomplete suggestion
 * (Requirement 9.7).
 *
 * <p>Carries only the human-readable label and the opaque provider place
 * reference; it deliberately holds no coordinate and no provider key. JSON
 * fields are camelCase.
 *
 * @param description the human-readable address label to display
 * @param placeId     the opaque provider reference used to resolve the suggestion
 */
public record AddressSuggestionResponse(String description, String placeId) {
}
