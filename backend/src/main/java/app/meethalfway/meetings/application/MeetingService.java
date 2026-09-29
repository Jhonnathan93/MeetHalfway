package app.meethalfway.meetings.application;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.shared.domain.TransportMode;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application operations for creating, retrieving, editing, and deleting meetings. */
@Service
public final class MeetingService {

    private final MeetingRepository repository;
    private final UrlCodeGenerator urlCodeGenerator;

    public MeetingService(MeetingRepository repository, UrlCodeGenerator urlCodeGenerator) {
        this.repository = repository;
        this.urlCodeGenerator = urlCodeGenerator;
    }

    public Meeting create(List<NewParticipant> participants, TransportMode mode) {
        MeetingInput input = toMeetingInput(participants, mode);
        String urlCode = urlCodeGenerator.generate(
                code -> repository.findByUrlCode(code).isPresent());
        return repository.save(Meeting.of(urlCode, input));
    }

    public Optional<Meeting> byUrlCode(String urlCode) {
        return repository.findByUrlCode(urlCode);
    }

    public Meeting edit(String urlCode, List<NewParticipant> participants, TransportMode mode) {
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        if (repository.findByUrlCode(urlCode).isEmpty()) {
            throw new NoSuchElementException("no meeting found for url code: " + urlCode);
        }
        return repository.save(Meeting.of(urlCode, toMeetingInput(participants, mode)));
    }

    public void deleteByUrlCode(String urlCode) {
        repository.deleteByUrlCode(urlCode);
    }

    private static MeetingInput toMeetingInput(List<NewParticipant> participants, TransportMode mode) {
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
        return new MeetingInput(inputs, mode);
    }

    /** Participant data before the application assigns a stable ID. */
    public record NewParticipant(String name, Coordinate location) {
        public NewParticipant {
            if (location == null) {
                throw new IllegalArgumentException("participant location must not be null");
            }
        }
    }
}
