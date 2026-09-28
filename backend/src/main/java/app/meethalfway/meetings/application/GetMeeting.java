package app.meethalfway.meetings.application;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import java.util.Optional;

/**
 * Use case: retrieve a meeting by its URL code (Requirement 9.2, 9.3). There is
 * no identity control — access is by {@code urlCode} alone. Returns
 * {@link Optional#empty()} when no meeting has that code, letting the caller
 * surface a not-found response.
 *
 * <p>Framework-free application code with explicit constructor injection.
 */
public final class GetMeeting {

    private final MeetingRepository repository;

    /**
     * @param repository the meeting repository port; must not be {@code null}
     */
    public GetMeeting(MeetingRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /**
     * Retrieves the meeting with the given URL code.
     *
     * @param urlCode the short access code
     * @return the meeting, or {@link Optional#empty()} if none has that code
     */
    public Optional<Meeting> byUrlCode(String urlCode) {
        return repository.findByUrlCode(urlCode);
    }
}
