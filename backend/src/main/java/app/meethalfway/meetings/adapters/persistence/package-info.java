/**
 * Meetings module persistence adapter — the outbound JPA/PostgreSQL edge.
 *
 * <p>Contains the JPA entities ({@link app.meethalfway.meetings.adapters.persistence.MeetingEntity},
 * {@link app.meethalfway.meetings.adapters.persistence.ParticipantEntity},
 * {@link app.meethalfway.meetings.adapters.persistence.RecommendationEntity}) that mirror
 * the framework-free domain aggregate {@link app.meethalfway.meetings.domain.model.Meeting},
 * and the {@link app.meethalfway.meetings.adapters.persistence.MeetingMapper}
 * that converts between the two, plus the {@link app.meethalfway.meetings.adapters.persistence.JpaMeetingRepository}
 * implementation of the domain repository port and the Spring Data interface.
 *
 * <p><strong>Dependency rule:</strong> all JPA annotations and PostgreSQL-specific
 * details live here, never in the {@code domain} layer. The domain stays pure so
 * the {@code ArchitectureRulesTest} "domain is framework-free" rule holds.
 */
package app.meethalfway.meetings.adapters.persistence;
