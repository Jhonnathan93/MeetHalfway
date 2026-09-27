package app.meethalfway.adapters.web.dto;

import java.util.List;

/**
 * The HTTP 422 body returned when a recommendation cannot be computed because
 * one or more participant locations could not be routed (Requirement 6).
 *
 * <p>The engine never silently drops a participant: instead it reports each
 * affected location with a specific reason, and this envelope adds an actionable
 * top-level {@code message} asking the Creator to correct or remove the listed
 * location(s) before recomputing (Requirement 6.2). JSON fields are camelCase.
 *
 * @param code    a stable machine-readable code ({@code ROUTING_FAILURE})
 * @param message an actionable top-level instruction to the Creator
 * @param errors  one entry per affected participant location
 */
public record RoutingFailureResponse(
        String code,
        String message,
        List<RoutingErrorResponse> errors) {
}
