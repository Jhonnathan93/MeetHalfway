package app.meethalfway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.application.MeetingService;
import app.meethalfway.meetings.application.UrlCodeGenerator;
import app.meethalfway.meetings.application.MeetingService.NewParticipant;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for meeting CRUD operations against the in-memory repository.
 */
class MeetingUseCasesTest {

    private final InMemoryMeetingRepository repository = new InMemoryMeetingRepository();
    private final UrlCodeGenerator urlCodeGenerator = new UrlCodeGenerator();
    private final MeetingService meetings = new MeetingService(repository, urlCodeGenerator);

    @Test
    void createPersistsAMeetingWithGeneratedUrlCodeAndUuidParticipantIds() {
        Meeting created = meetings.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING);

        assertThat(created.urlCode()).isNotBlank();
        assertThat(repository.findByUrlCode(created.urlCode())).isPresent();
        assertThat(created.input().participants()).hasSize(2);
        // Participant ids are minted as UUID strings for lossless persistence.
        for (ParticipantInput participant : created.input().participants()) {
            assertThat(UUID.fromString(participant.id().value())).isNotNull();
        }
        assertThat(created.recommendation()).isEmpty();
    }

    @Test
    void createRejectsFewerThanTwoParticipants() {
        assertThatThrownBy(() -> meetings.create(
                List.of(new NewParticipant("Solo", new Coordinate(6.24, -75.58))),
                TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsTheStoredMeeting() {
        Meeting created = meetings.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.WALKING);

        assertThat(meetings.byUrlCode(created.urlCode())).contains(created);
        assertThat(meetings.byUrlCode("UNKNOWN0")).isEmpty();
    }

    @Test
    void editReplacesParticipantsAndModeUnderTheSameUrlCode() {
        Meeting created = meetings.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING);

        Meeting edited = meetings.edit(
                created.urlCode(),
                List.of(
                        new NewParticipant("Carla", new Coordinate(6.20, -75.60)),
                        new NewParticipant("Diego", new Coordinate(6.21, -75.61)),
                        new NewParticipant("Eva", new Coordinate(6.22, -75.62))),
                TransportMode.WALKING);

        assertThat(edited.urlCode()).isEqualTo(created.urlCode());
        assertThat(edited.input().mode()).isEqualTo(TransportMode.WALKING);
        assertThat(edited.input().participants()).hasSize(3);
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void editUnknownMeetingThrows() {
        assertThatThrownBy(() -> meetings.edit(
                "MISSING0",
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteRemovesTheMeeting() {
        Meeting created = meetings.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING);

        meetings.deleteByUrlCode(created.urlCode());

        assertThat(repository.findByUrlCode(created.urlCode())).isEmpty();
    }

}
