/**
 * Persistence adapter — the outbound JPA/PostgreSQL edge.
 *
 * <p>Contains the JPA entities ({@link app.meethalfway.adapters.persistence.MeetingEntity},
 * {@link app.meethalfway.adapters.persistence.ParticipantEntity},
 * {@link app.meethalfway.adapters.persistence.RecommendationEntity}) that mirror
 * the framework-free domain aggregate {@link app.meethalfway.domain.model.Meeting},
 * and the {@link app.meethalfway.adapters.persistence.MeetingPersistenceMapper}
 * that converts between the two.
 *
 * <p><strong>Dependency rule:</strong> all JPA annotations and PostgreSQL-specific
 * details live here, never in the {@code domain} layer. The domain stays pure so
 * the {@code ArchitectureRulesTest} "domain is framework-free" rule holds. The JPA
 * {@code MeetingRepository} implementation (task 9.2) will live alongside these
 * entities and use the mapper.
 */
package app.meethalfway.adapters.persistence;
