package app.meethalfway.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.MeetingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Example-based CRUD tests for {@link JpaMeetingRepository} (Task 9.4,
 * Requirements 9.1–9.6).
 *
 * <p>Like the round-trip property test, these run offline against a Map-backed
 * fake of the Spring Data repository ({@link InMemorySpringDataMeetingRepository})
 * because the {@code JSONB} column rules out an offline real-DB test with the
 * available dependencies. They verify create, edit-in-place (participants/mode
 * change), delete, and the history semantics that a second save under the same
 * URL code preserves {@code createdAt} and bumps {@code updatedAt} — asserted
 * with an advancing {@link Clock}.
 */
class JpaMeetingRepositoryCrudTest {

    private final InMemorySpringDataMeetingRepository springData =
            new InMemorySpringDataMeetingRepository();
    private final AdvancingClock clock = new AdvancingClock(
            Instant.parse("2024-01-01T00:00:00Z"), Duration.ofMinutes(1));
    private final MeetingRepository repository =
            new JpaMeetingRepository(springData, new MeetingMapper(), clock);

    @Test
    void savePersistsANewMeetingRetrievableByUrlCode() {
        Meeting meeting = meeting("ABCD1234", TransportMode.DRIVING,
                participant("Ana", 6.24, -75.58), participant("Bruno", 6.25, -75.56));

        repository.save(meeting);

        Optional<Meeting> loaded = repository.findByUrlCode("ABCD1234");
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().input().participants()).hasSize(2);
        assertThat(loaded.orElseThrow().input().mode()).isEqualTo(TransportMode.DRIVING);
    }

    @Test
    void editChangesParticipantsAndModeInPlace() {
        Meeting original = meeting("EDIT0001", TransportMode.DRIVING,
                participant("Ana", 6.24, -75.58), participant("Bruno", 6.25, -75.56));
        repository.save(original);

        Meeting edited = meeting("EDIT0001", TransportMode.WALKING,
                participant("Carla", 6.20, -75.60),
                participant("Diego", 6.21, -75.61),
                participant("Eva", 6.22, -75.62));
        repository.save(edited);

        Meeting loaded = repository.findByUrlCode("EDIT0001").orElseThrow();
        assertThat(loaded.input().mode()).isEqualTo(TransportMode.WALKING);
        assertThat(loaded.input().participants()).hasSize(3);
        assertThat(loaded.input().participants().stream().map(ParticipantInput::name))
                .containsExactly("Carla", "Diego", "Eva");
        // Exactly one row remains for this URL code (in-place update, not insert).
        assertThat(springData.count()).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheMeeting() {
        repository.save(meeting("DEL00001", TransportMode.DRIVING,
                participant("Ana", 6.24, -75.58), participant("Bruno", 6.25, -75.56)));
        assertThat(repository.findByUrlCode("DEL00001")).isPresent();

        repository.deleteByUrlCode("DEL00001");

        assertThat(repository.findByUrlCode("DEL00001")).isEmpty();
    }

    @Test
    void deletingAnUnknownCodeIsANoOp() {
        org.assertj.core.api.Assertions.assertThatCode(() -> repository.deleteByUrlCode("MISSING0"))
                .doesNotThrowAnyException();
        assertThat(repository.findByUrlCode("MISSING0")).isEmpty();
    }

    @Test
    void secondSaveUpdatesInPlacePreservingCreatedAtAndBumpingUpdatedAt() {
        Meeting original = meeting("HIST0001", TransportMode.DRIVING,
                participant("Ana", 6.24, -75.58), participant("Bruno", 6.25, -75.56));
        repository.save(original); // consumes tick #1 (00:00) for both timestamps

        UUID idAfterCreate = storedEntity("HIST0001").getId();
        OffsetDateTime createdAt = storedEntity("HIST0001").getCreatedAt();
        OffsetDateTime updatedAtAfterCreate = storedEntity("HIST0001").getUpdatedAt();
        assertThat(createdAt).isEqualTo(updatedAtAfterCreate);

        Meeting edited = meeting("HIST0001", TransportMode.WALKING,
                participant("Ana", 6.24, -75.58), participant("Bruno", 6.25, -75.56));
        repository.save(edited); // consumes tick #2 (00:01) for updatedAt only

        MeetingEntity afterUpdate = storedEntity("HIST0001");
        assertThat(afterUpdate.getId()).isEqualTo(idAfterCreate);
        assertThat(afterUpdate.getCreatedAt()).isEqualTo(createdAt);
        assertThat(afterUpdate.getUpdatedAt()).isAfter(createdAt);
    }

    private MeetingEntity storedEntity(String urlCode) {
        return springData.findByUrlCode(urlCode).orElseThrow();
    }

    private static Meeting meeting(String urlCode, TransportMode mode, ParticipantInput... participants) {
        // A mutable list: MeetingInput's null-check calls List.contains(null), which
        // throws on JDK immutable lists (List.of), so pass an ArrayList.
        return Meeting.of(urlCode, new MeetingInput(new ArrayList<>(List.of(participants)), mode));
    }

    private static ParticipantInput participant(String name, double lat, double lng) {
        return new ParticipantInput(
                new ParticipantId(UUID.randomUUID().toString()), name, new Coordinate(lat, lng));
    }

    /**
     * A {@link Clock} that advances by a fixed step every time it is read, so each
     * {@code save} sees a strictly-later "now". This makes the created-vs-updated
     * timestamp semantics deterministically observable.
     */
    private static final class AdvancingClock extends Clock {
        private Instant current;
        private final Duration step;

        private AdvancingClock(Instant start, Duration step) {
            this.current = start;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant now = current;
            current = current.plus(step);
            return now;
        }
    }
}
