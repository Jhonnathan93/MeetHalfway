package app.meethalfway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.application.CreateMeeting.NewParticipant;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.TransportMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the framework-free meeting CRUD use cases (Task 11.1):
 * {@link CreateMeeting}, {@link GetMeeting}, {@link EditMeeting}, and
 * {@link DeleteMeeting}. They run against the in-memory {@link MeetingRepository}
 * fake with no Spring context.
 */
class MeetingUseCasesTest {

    private final InMemoryMeetingRepository repository = new InMemoryMeetingRepository();
    private final UrlCodeGenerator urlCodeGenerator = new UrlCodeGenerator();

    @Test
    void createPersistsAMeetingWithGeneratedUrlCodeAndUuidParticipantIds() {
        CreateMeeting createMeeting = new CreateMeeting(repository, urlCodeGenerator);

        Meeting created = createMeeting.create(
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
        CreateMeeting createMeeting = new CreateMeeting(repository, urlCodeGenerator);

        assertThatThrownBy(() -> createMeeting.create(
                List.of(new NewParticipant("Solo", new Coordinate(6.24, -75.58))),
                TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsTheStoredMeeting() {
        CreateMeeting createMeeting = new CreateMeeting(repository, urlCodeGenerator);
        Meeting created = createMeeting.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.WALKING);

        GetMeeting getMeeting = new GetMeeting(repository);

        assertThat(getMeeting.byUrlCode(created.urlCode())).contains(created);
        assertThat(getMeeting.byUrlCode("UNKNOWN0")).isEmpty();
    }

    @Test
    void editReplacesParticipantsAndModeUnderTheSameUrlCode() {
        CreateMeeting createMeeting = new CreateMeeting(repository, urlCodeGenerator);
        Meeting created = createMeeting.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING);

        EditMeeting editMeeting = new EditMeeting(repository);
        Meeting edited = editMeeting.edit(
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
        EditMeeting editMeeting = new EditMeeting(repository);

        assertThatThrownBy(() -> editMeeting.edit(
                "MISSING0",
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteRemovesTheMeeting() {
        CreateMeeting createMeeting = new CreateMeeting(repository, urlCodeGenerator);
        Meeting created = createMeeting.create(
                List.of(
                        new NewParticipant("Ana", new Coordinate(6.24, -75.58)),
                        new NewParticipant("Bruno", new Coordinate(6.25, -75.56))),
                TransportMode.DRIVING);

        DeleteMeeting deleteMeeting = new DeleteMeeting(repository);
        deleteMeeting.byUrlCode(created.urlCode());

        assertThat(repository.findByUrlCode(created.urlCode())).isEmpty();
    }

    @Test
    void constructorsRejectNullDependencies() {
        assertThatThrownBy(() -> new CreateMeeting(null, urlCodeGenerator))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GetMeeting(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EditMeeting(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeleteMeeting(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
