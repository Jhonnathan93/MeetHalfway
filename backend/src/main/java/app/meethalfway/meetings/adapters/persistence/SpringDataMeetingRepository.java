package app.meethalfway.meetings.adapters.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository over {@link MeetingEntity} (Requirement 9). This is
 * a framework-facing interface in the {@code adapters} layer; the domain never
 * references it. The domain {@code MeetingRepository} port is implemented by
 * {@link JpaMeetingRepository}, which delegates persistence to this repository
 * and maps between entities and the framework-free
 * {@link app.meethalfway.meetings.domain.model.Meeting} aggregate.
 *
 * <p>Access is by {@code urlCode} alone — there is no identity control
 * (Requirement 9.2).
 */
public interface SpringDataMeetingRepository extends JpaRepository<MeetingEntity, UUID> {

    /**
     * Loads a meeting (and, through its mappings, its participants and
     * recommendations) by its unique URL code.
     *
     * @param urlCode the short access code
     * @return the entity, or {@link Optional#empty()} if none has that code
     */
    Optional<MeetingEntity> findByUrlCode(String urlCode);

    /**
     * Tests whether a meeting already uses the given URL code. Used by the URL
     * code generator to guarantee collision-free codes.
     *
     * @param urlCode the candidate access code
     * @return {@code true} when a meeting already has that code
     */
    boolean existsByUrlCode(String urlCode);

    /**
     * Deletes the meeting with the given URL code, if any. Deleting a code that
     * does not exist is a no-op (Requirement 9.5).
     *
     * @param urlCode the short access code of the meeting to remove
     */
    void deleteByUrlCode(String urlCode);
}
