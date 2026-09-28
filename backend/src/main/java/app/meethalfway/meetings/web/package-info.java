/**
 * Meetings module HTTP edge.
 *
 * <p>Holds {@code MeetingController}, the inbound REST adapter for the meetings
 * CRUD and recommendations endpoints. The geocoding endpoints remain here
 * temporarily and move to {@code LocationController} in a later migration step
 * (R2.5). The controller stays thin: it binds and validates requests, delegates
 * to a single application use case, and maps results through the module
 * {@code WebMapper}.
 */
package app.meethalfway.meetings.web;
