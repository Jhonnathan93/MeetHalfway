package app.meethalfway.shared.web.dto;

import app.meethalfway.shared.domain.Coordinate;

/**
 * Wire representation of a geographic point ({@code lat}/{@code lng}) in
 * responses. Kept separate from the domain {@code Coordinate} so the JSON
 * contract is explicit and independent of domain record component names.
 *
 * @param lat latitude in degrees
 * @param lng longitude in degrees
 */
public record CoordinateResponse(double lat, double lng) {
}
