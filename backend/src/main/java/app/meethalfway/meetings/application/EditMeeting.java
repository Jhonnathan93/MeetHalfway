package app.meethalfway.meetings.application;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.domain.model.MeetingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case: edit an existing meeting's participants and/or transport mode
 * (Requirements 9.2, 9.4). The Creator re-enters all participant data (product
 * rule), so each edited participant is assigned a fresh UUID-string
 * {@link ParticipantId}. The meeting is looked up by {@code urlCode} with no
 * identity control; its persistence identity and creation timestamp are
 * preserved on save (the repository updates in place and bumps
 * {@code updatedAt}).
 *
 * <p>Because the participant set changes, any previously stored recommendation
 * is invalidated and dropped; a new computation must be run afterwards. This is
 * framework-free application code with explicit constructor injection.
 */
public final class EditMeeting {

    private final MeetingRepository repository;

    /**
     * @param repository the meeting repository port; must not be {@code null}
     */
    public EditMeeting(MeetingRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /**
     * Replaces the participants and transport mode of the meeting identified by
     * {@code urlCode}, then persists it. All domain invariants are enforced by
     * the value objects. The stored recommendation, if any, is cleared because
     * the inputs changed.
     *
     * @param urlCode      the access code of the meeting to edit
     * @param participants the replacement participants (2–10); must not be
     *                     {@code null} or contain {@code null}
     * @param mode         the replacement transport mode; must not be {@code null}
     * @return the updated, persisted meeting
     * @throws NoSuchElementException if no meeting has the given URL code
     */
    public Meeting edit(String urlCode, List<CreateMeeting.NewParticipant> participants, TransportMode mode) {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        Optional<Meeting> existing = repository.findByUrlCode(urlCode);
        if (existing.isEmpty()) {
            throw new NoSuchElementException("no meeting found for url code: " + urlCode);
        }

        List<ParticipantInput> inputs = new ArrayList<>(participants.size());
        for (CreateMeeting.NewParticipant participant : participants) {
            if (participant == null) {
                throw new IllegalArgumentException("participants must not contain a null entry");
            }
            inputs.add(new ParticipantInput(
                    new ParticipantId(UUID.randomUUID().toString()),
                    participant.name(),
                    participant.location()));
        }
        MeetingInput input = new MeetingInput(inputs, mode);

        // Drop the stale recommendation: the inputs changed, so any prior outcome
        // no longer describes this meeting. Meeting.of yields an empty outcome.
        return repository.save(Meeting.of(urlCode, input));
    }
}
