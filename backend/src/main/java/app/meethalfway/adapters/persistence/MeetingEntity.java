package app.meethalfway.adapters.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code meeting} table (see {@code V1__create_meeting_schema.sql}).
 *
 * <p>Mirrors the framework-free {@link app.meethalfway.domain.model.Meeting}
 * aggregate but carries all persistence concerns. Per the design's PostgreSQL
 * schema and Requirement 9.2, there is <strong>no user identity</strong> column:
 * access is by {@code urlCode} alone. {@code createdAt}/{@code updatedAt} support
 * history retention (Requirement 9.6).
 *
 * <p>{@code transportMode} is stored as its lowercase wire form
 * ({@code "driving"} / {@code "walking"}) to match the DB {@code CHECK}
 * constraint; the mapper translates to/from the domain enum.
 */
@Entity
@Table(name = "meeting")
public class MeetingEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "url_code", nullable = false, unique = true, length = 64)
    private String urlCode;

    @Column(name = "transport_mode", nullable = false, length = 16)
    private String transportMode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(
            mappedBy = "meeting",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ParticipantEntity> participants = new ArrayList<>();

    @OneToMany(
            mappedBy = "meeting",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<RecommendationEntity> recommendations = new ArrayList<>();

    /** Required no-args constructor for JPA. */
    protected MeetingEntity() {
    }

    public MeetingEntity(
            UUID id,
            String urlCode,
            String transportMode,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.urlCode = urlCode;
        this.transportMode = transportMode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Adds a participant and keeps both sides of the bidirectional relationship
     * consistent.
     *
     * @param participant the participant to attach (must not be {@code null})
     */
    public void addParticipant(ParticipantEntity participant) {
        participants.add(participant);
        participant.setMeeting(this);
    }

    /**
     * Adds a recommendation and keeps both sides of the bidirectional
     * relationship consistent.
     *
     * @param recommendation the recommendation to attach (must not be {@code null})
     */
    public void addRecommendation(RecommendationEntity recommendation) {
        recommendations.add(recommendation);
        recommendation.setMeeting(this);
    }

    public UUID getId() {
        return id;
    }

    public String getUrlCode() {
        return urlCode;
    }

    public void setUrlCode(String urlCode) {
        this.urlCode = urlCode;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ParticipantEntity> getParticipants() {
        return participants;
    }

    public List<RecommendationEntity> getRecommendations() {
        return recommendations;
    }
}
