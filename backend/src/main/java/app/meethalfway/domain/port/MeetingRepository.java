package app.meethalfway.domain.port;

import app.meethalfway.domain.model.Meeting;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving meetings (Requirement 9). The core
 * depends only on this interface; a JPA/PostgreSQL implementation lives in the
 * {@code adapters} layer and is injected explicitly, keeping the domain free of
 * persistence details (Clean Architecture dependency rule).
 *
 * <p>Access is by {@code urlCode} alone — there is no identity control anywhere
 * in this port (Requirement 9.2).
 */
public interface MeetingRepository {

    /**
     * Persists a meeting, creating it or updating it in place, and returns the
     * stored aggregate (Requirement 9.1, 9.4).
     *
     * @param meeting the meeting to persist; must not be {@code null}
     * @return the persisted meeting
     */
    Meeting save(Meeting meeting);

    /**
     * Retrieves a meeting by its URL code without any identity control
     * (Requirement 9.2, 9.3).
     *
     * @param urlCode the short access code
     * @return the meeting, or {@link Optional#empty()} if no meeting has that code
     */
    Optional<Meeting> findByUrlCode(String urlCode);

    /**
     * Removes a meeting from persistent storage (Requirement 9.5). Deleting a
     * code that does not exist is a no-op.
     *
     * @param urlCode the short access code of the meeting to remove
     */
    void deleteByUrlCode(String urlCode);
}
