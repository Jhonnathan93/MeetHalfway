package app.meethalfway.adapters.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

/**
 * A Map-backed, hand-written fake of {@link SpringDataMeetingRepository} for
 * offline persistence tests.
 *
 * <p><b>Why a fake instead of a real DB?</b> The {@code recommendation
 * .per_participant_times} column is PostgreSQL {@code JSONB}. Testcontainers is
 * not available in the offline dependency cache, and no in-memory database (H2)
 * is a dependency either, so a real-DB integration test cannot run here. To keep
 * the persistence round-trip (Property 17) and the CRUD behaviors verifiable
 * offline with zero new dependencies, these tests exercise the
 * {@link JpaMeetingRepository} adapter and {@link MeetingMapper} against this
 * in-memory stub of the Spring Data repository.
 *
 * <p>Only the repository methods the adapter and URL-code generator actually use
 * are implemented: {@link #findByUrlCode}, {@link #existsByUrlCode},
 * {@link #deleteByUrlCode}, and {@link #save}. Every other {@code JpaRepository}
 * method throws {@link UnsupportedOperationException} so accidental reliance on
 * unimplemented behavior fails loudly.
 *
 * <p>To emulate JPA detachment (so a later in-place update does not retroactively
 * mutate a previously-returned aggregate), {@link #save} and {@link #findByUrlCode}
 * deep-copy the entity graph on the way in and out.
 */
final class InMemorySpringDataMeetingRepository implements SpringDataMeetingRepository {

    private final Map<UUID, MeetingEntity> byId = new LinkedHashMap<>();

    @Override
    public Optional<MeetingEntity> findByUrlCode(String urlCode) {
        return byId.values().stream()
                .filter(entity -> entity.getUrlCode().equals(urlCode))
                .findFirst()
                .map(InMemorySpringDataMeetingRepository::copy);
    }

    @Override
    public boolean existsByUrlCode(String urlCode) {
        return byId.values().stream().anyMatch(entity -> entity.getUrlCode().equals(urlCode));
    }

    @Override
    public void deleteByUrlCode(String urlCode) {
        byId.values().removeIf(entity -> entity.getUrlCode().equals(urlCode));
    }

    @Override
    public <S extends MeetingEntity> S save(S entity) {
        byId.put(entity.getId(), copy(entity));
        return entity;
    }

    /** Deep-copies a meeting entity graph so stored and returned copies are detached. */
    private static MeetingEntity copy(MeetingEntity source) {
        MeetingEntity copy = new MeetingEntity(
                source.getId(),
                source.getUrlCode(),
                source.getTransportMode(),
                source.getCreatedAt(),
                source.getUpdatedAt());
        for (ParticipantEntity participant : source.getParticipants()) {
            copy.addParticipant(new ParticipantEntity(
                    participant.getId(),
                    participant.getName(),
                    participant.getLat(),
                    participant.getLng()));
        }
        for (RecommendationEntity recommendation : source.getRecommendations()) {
            copy.addRecommendation(new RecommendationEntity(
                    recommendation.getId(),
                    recommendation.getStrategy(),
                    recommendation.isExcludesOutlier(),
                    recommendation.getPointLat(),
                    recommendation.getPointLng(),
                    recommendation.getSumTime(),
                    recommendation.getMaxTime(),
                    recommendation.getStdDev(),
                    recommendation.getPerParticipantTimes()));
        }
        return copy;
    }

    // ---- Unused JpaRepository surface: fail loudly if ever exercised. ----

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not needed for offline persistence tests");
    }

    @Override
    public Optional<MeetingEntity> findById(UUID uuid) {
        return Optional.ofNullable(byId.get(uuid)).map(InMemorySpringDataMeetingRepository::copy);
    }

    @Override
    public boolean existsById(UUID uuid) {
        return byId.containsKey(uuid);
    }

    @Override
    public List<MeetingEntity> findAll() {
        List<MeetingEntity> all = new ArrayList<>();
        byId.values().forEach(entity -> all.add(copy(entity)));
        return all;
    }

    @Override
    public long count() {
        return byId.size();
    }

    @Override
    public void deleteById(UUID uuid) {
        byId.remove(uuid);
    }

    @Override
    public void delete(MeetingEntity entity) {
        byId.remove(entity.getId());
    }

    @Override
    public void deleteAll() {
        byId.clear();
    }

    @Override
    public <S extends MeetingEntity> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public List<MeetingEntity> findAllById(Iterable<UUID> uuids) {
        throw unsupported();
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> uuids) {
        throw unsupported();
    }

    @Override
    public void deleteAll(Iterable<? extends MeetingEntity> entities) {
        throw unsupported();
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public <S extends MeetingEntity> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends MeetingEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<MeetingEntity> entities) {
        throw unsupported();
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
        throw unsupported();
    }

    @Override
    public void deleteAllInBatch() {
        throw unsupported();
    }

    @Override
    public MeetingEntity getOne(UUID uuid) {
        throw unsupported();
    }

    @Override
    public MeetingEntity getById(UUID uuid) {
        throw unsupported();
    }

    @Override
    public MeetingEntity getReferenceById(UUID uuid) {
        throw unsupported();
    }

    @Override
    public List<MeetingEntity> findAll(Sort sort) {
        throw unsupported();
    }

    @Override
    public Page<MeetingEntity> findAll(Pageable pageable) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> Optional<S> findOne(Example<S> example) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> List<S> findAll(Example<S> example) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> List<S> findAll(Example<S> example, Sort sort) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> long count(Example<S> example) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity> boolean exists(Example<S> example) {
        throw unsupported();
    }

    @Override
    public <S extends MeetingEntity, R> R findBy(
            Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw unsupported();
    }
}
