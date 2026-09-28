package app.meethalfway.meetings.adapters.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code recommendation} table (see
 * {@code V1__create_meeting_schema.sql}).
 *
 * <p>A single row is one strategy result ({@code FASTEST} / {@code MINIMAX} /
 * {@code FAIREST}) for one variant. {@code excludesOutlier} distinguishes the
 * including variant ({@code false}) from the excluding variant ({@code true}) so
 * one table holds both sides of an outlier trade-off (Requirement 7). A meeting
 * with no outlier stores three rows (all {@code excludesOutlier = false}); a
 * meeting with an outlier trade-off stores six.
 *
 * <p>{@code perParticipantTimes} maps a participant id to whole minutes and is
 * persisted as PostgreSQL {@code JSONB} via Hibernate's {@code SqlTypes.JSON}
 * mapping — no domain type is exposed to the database.
 */
@Entity
@Table(name = "recommendation")
public class RecommendationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private MeetingEntity meeting;

    @Column(name = "strategy", nullable = false, length = 16)
    private String strategy;

    @Column(name = "excludes_outlier", nullable = false)
    private boolean excludesOutlier;

    @Column(name = "point_lat", nullable = false)
    private double pointLat;

    @Column(name = "point_lng", nullable = false)
    private double pointLng;

    @Column(name = "sum_time", nullable = false)
    private double sumTime;

    @Column(name = "max_time", nullable = false)
    private int maxTime;

    @Column(name = "std_dev", nullable = false)
    private double stdDev;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "per_participant_times", nullable = false)
    private Map<String, Integer> perParticipantTimes = new LinkedHashMap<>();

    /** Required no-args constructor for JPA. */
    protected RecommendationEntity() {
    }

    public RecommendationEntity(
            UUID id,
            String strategy,
            boolean excludesOutlier,
            double pointLat,
            double pointLng,
            double sumTime,
            int maxTime,
            double stdDev,
            Map<String, Integer> perParticipantTimes) {
        this.id = id;
        this.strategy = strategy;
        this.excludesOutlier = excludesOutlier;
        this.pointLat = pointLat;
        this.pointLng = pointLng;
        this.sumTime = sumTime;
        this.maxTime = maxTime;
        this.stdDev = stdDev;
        this.perParticipantTimes = new LinkedHashMap<>(perParticipantTimes);
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

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public boolean isExcludesOutlier() {
        return excludesOutlier;
    }

    public void setExcludesOutlier(boolean excludesOutlier) {
        this.excludesOutlier = excludesOutlier;
    }

    public double getPointLat() {
        return pointLat;
    }

    public void setPointLat(double pointLat) {
        this.pointLat = pointLat;
    }

    public double getPointLng() {
        return pointLng;
    }

    public void setPointLng(double pointLng) {
        this.pointLng = pointLng;
    }

    public double getSumTime() {
        return sumTime;
    }

    public void setSumTime(double sumTime) {
        this.sumTime = sumTime;
    }

    public int getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(int maxTime) {
        this.maxTime = maxTime;
    }

    public double getStdDev() {
        return stdDev;
    }

    public void setStdDev(double stdDev) {
        this.stdDev = stdDev;
    }

    public Map<String, Integer> getPerParticipantTimes() {
        return perParticipantTimes;
    }

    public void setPerParticipantTimes(Map<String, Integer> perParticipantTimes) {
        this.perParticipantTimes = new LinkedHashMap<>(perParticipantTimes);
    }
}
