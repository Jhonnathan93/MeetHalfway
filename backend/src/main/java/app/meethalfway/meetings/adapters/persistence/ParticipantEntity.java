package app.meethalfway.meetings.adapters.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * JPA entity for the {@code participant} table (see
 * {@code V1__create_meeting_schema.sql}).
 *
 * <p>Mirrors a {@link app.meethalfway.meetings.domain.model.ParticipantInput}: a name and
 * a latitude/longitude location, owned by a {@link MeetingEntity}. The
 * {@code id} is the persistence identity; the domain {@code ParticipantId} value
 * is derived from it by the mapper.
 */
@Entity
@Table(name = "participant")
public class ParticipantEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private MeetingEntity meeting;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    /** Required no-args constructor for JPA. */
    protected ParticipantEntity() {
    }

    public ParticipantEntity(UUID id, String name, double lat, double lng) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public UUID getId() {
        return id;
    }

    public MeetingEntity getMeeting() {
        return meeting;
    }

    void setMeeting(MeetingEntity meeting) {
        this.meeting = meeting;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}
