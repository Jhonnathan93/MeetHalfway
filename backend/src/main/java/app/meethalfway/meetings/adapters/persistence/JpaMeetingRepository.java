package app.meethalfway.meetings.adapters.persistence;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA/PostgreSQL implementation of the domain {@link MeetingRepository} port
 * (Requirement 9). It adapts the framework-free {@link Meeting} aggregate to the
 * JPA entity graph via {@link MeetingMapper} and delegates storage to
 * {@link SpringDataMeetingRepository}. Dependencies are supplied through explicit
 * constructor injection (no field injection); a {@link Clock} is injected so
 * creation/update timestamps are deterministic and testable.
 *
 * <p>Access is by {@code urlCode} alone; there is no identity control
 * (Requirement 9.2).
 */
public class JpaMeetingRepository implements MeetingRepository {

    private final SpringDataMeetingRepository repository;
    private final MeetingMapper mapper;
    private final Clock clock;

    /**
     * @param repository the Spring Data JPA repository; must not be {@code null}
     * @param mapper     the entity/domain mapper; must not be {@code null}
     * @param clock      the clock used for timestamps; must not be {@code null}
     * @throws IllegalArgumentException if any dependency is {@code null}
     */
    public JpaMeetingRepository(
            SpringDataMeetingRepository repository, MeetingMapper mapper, Clock clock) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Persists a meeting (Requirement 9.1, 9.4). If a row already exists for the
     * meeting's {@code urlCode}, it is updated in place — its identity and
     * {@code createdAt} are preserved and {@code updatedAt} is bumped to now.
     * Otherwise a new row is inserted with a fresh UUID and
     * {@code createdAt == updatedAt == now} (history retention, Requirement 9.6).
     *
     * @param meeting the meeting to persist; must not be {@code null}
     * @return the persisted meeting mapped back to the domain
     */
    @Override
    @Transactional
    public Meeting save(Meeting meeting) {
        if (meeting == null) {
            throw new IllegalArgumentException("meeting must not be null");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<MeetingEntity> existing = repository.findByUrlCode(meeting.urlCode());

        MeetingEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, meeting);
            entity.setUpdatedAt(now);
        } else {
            entity = mapper.toEntity(meeting, UUID.randomUUID(), now, now);
        }
        MeetingEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Meeting> findByUrlCode(String urlCode) {
        return repository.findByUrlCode(urlCode).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUrlCode(String urlCode) {
        repository.deleteByUrlCode(urlCode);
    }
}
