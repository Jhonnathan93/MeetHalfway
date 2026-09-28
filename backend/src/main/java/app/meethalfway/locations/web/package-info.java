/**
 * Locations module HTTP edge.
 *
 * <p>Home of {@code LocationController}, which serves the geocoding endpoints
 * ({@code GET /geocode/autocomplete}, {@code GET /geocode/resolve}) split out of
 * {@code MeetingController} per R2.5. The controller is thin: it delegates to the
 * {@code GeocodingProvider} port and maps results via {@code LocationMapper}.
 */
package app.meethalfway.locations.web;
