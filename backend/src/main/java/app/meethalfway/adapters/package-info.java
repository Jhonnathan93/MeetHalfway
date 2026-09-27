/**
 * Adapters layer — the edges of the system.
 *
 * <p>Contains inbound adapters (the REST web adapter under {@code /api/v1}) and
 * outbound adapters (routing, geocoding, places, and the JPA persistence adapter).
 * Outbound adapters <em>implement</em> the ports defined in the {@code domain}
 * layer.
 *
 * <p><strong>Dependency rule:</strong> adapters may depend on both {@code domain}
 * and {@code application} (they implement domain ports and are invoked by / invoke
 * application use cases). Nothing inner may depend back on this layer.
 */
package app.meethalfway.adapters;
