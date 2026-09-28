package app.meethalfway.meetings.application;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Use case: create a new meeting (Requirements 1, 9.1). It validates the input
 * through the domain value objects (participant count 2–10, coordinate ranges,
 * supported transport mode), mints a stable {@link ParticipantId} per participant
 * as a UUID string (the persistence contract for lossless
 * {@code ParticipantId ↔ UUID} round-tripping), generates a collision-free URL
 * code, persists the meeting, and returns the stored aggregate.
 *
 * <p>This is framework-free application code with explicit constructor injection
 * and no Spring annotations; it is wired as a bean at the composition root. It
 * depends only on the domain {@link MeetingRepository} port and the
 * {@link UrlCodeGenerator}.
 */
public final class CreateMeeting {

    private final MeetingRepository repository;
    private final UrlCodeGenerator urlCodeGenerator;

    /**
     * @param repository       the meeting repository port; must not be {@code null}
     * @param urlCodeGenerator the URL code generator; must not be {@code null}
     */
    public CreateMeeting(MeetingRepository repository, UrlCodeGenerator urlCodeGenerator) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (urlCodeGenerator == null) {
            throw new IllegalArgumentException("urlCodeGenerator must not be null");
        }
        this.repository = repository;
        this.urlCodeGenerator = urlCodeGenerator;
    }

    /**
     * Creates and persists a meeting from raw participant data and a transport
     * mode. Each participant is assigned a freshly-minted UUID-string
     * {@link ParticipantId}. All domain invariants are enforced by the value
     * objects; invalid input throws {@link IllegalArgumentException}.
     *
     * @param participants the participants to create (2–10); must not be
     *                     {@code null} or contain {@code null}
     * @param mode         the shared transport mode; must not be {@code null}
     * @return the persisted meeting, including its generated URL code
     */
    public Meeting create(List<NewParticipant> participants, TransportMode mode) {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        List<ParticipantInput> inputs = new ArrayList<>(participants.size());
        for (NewParticipant participant : participants) {
            if (participant == null) {
                throw new IllegalArgumentException("participants must not contain a null entry");
            }
            inputs.add(new ParticipantInput(
                    new ParticipantId(UUID.randomUUID().toString()),
                    participant.name(),
                    participant.location()));
        }
        MeetingInput input = new MeetingInput(inputs, mode);

        String urlCode = urlCodeGenerator.generate(
                code -> repository.findByUrlCode(code).isPresent());

        return repository.save(Meeting.of(urlCode, input));
    }

    /**
     * A participant supplied when creating a meeting, before an identity is
     * assigned. The application mints the {@link ParticipantId}; callers provide
     * only the display name (optional) and location.
     *
     * @param name     the display name; may be {@code null} (normalized to empty)
     * @param location the starting coordinate; must not be {@code null}
     */
    public record NewParticipant(String name, Coordinate location) {

        public NewParticipant {
            if (location == null) {
                throw new IllegalArgumentException("participant location must not be null");
            }
        }
    }
}
