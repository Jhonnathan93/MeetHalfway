package app.meethalfway.application;

import app.meethalfway.domain.port.MeetingRepository;

/**
 * Use case: delete a meeting by its URL code (Requirement 9.5). There is no
 * identity control; deleting a code that does not exist is a no-op (the port
 * defines this contract).
 *
 * <p>Framework-free application code with explicit constructor injection.
 */
public final class DeleteMeeting {

    private final MeetingRepository repository;

    /**
     * @param repository the meeting repository port; must not be {@code null}
     */
    public DeleteMeeting(MeetingRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /**
     * Deletes the meeting with the given URL code, if present.
     *
     * @param urlCode the short access code of the meeting to remove
     */
    public void byUrlCode(String urlCode) {
        repository.deleteByUrlCode(urlCode);
    }
}
