/**
 * Locations module domain ports.
 *
 * <p>Home of the {@code GeocodingProvider} port (plus {@code GeocodeResult} and
 * {@code AddressSuggestion}) and the post-MVP {@code PlacesProvider} port (plus
 * {@code Place} and {@code PlaceQuery}). These are framework-free interfaces and
 * value types; concrete adapters live under {@code locations.adapters} and are
 * injected explicitly, keeping provider API keys backend-only.
 */
package app.meethalfway.locations.domain.port;
