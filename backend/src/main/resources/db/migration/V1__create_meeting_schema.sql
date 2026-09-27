-- MeetHalfway meeting-recommendation-engine: initial persistence schema.
--
-- Design reference: .kiro/specs/meeting-recommendation-engine/design.md
-- (PostgreSQL Schema ER diagram) and Requirements 9.1, 9.2, 9.6.
--
-- Non-negotiable constraints reflected here:
--   * No authentication and no user identity anywhere in the schema (Req 9.2).
--     Access is by url_code alone.
--   * transport_mode is stored as its lowercase wire form ('driving' | 'walking').
--   * RECOMMENDATION.strategy is stored as its uppercase form
--     ('FASTEST' | 'MINIMAX' | 'FAIREST').
--   * excludes_outlier lets a single table hold both the including (false) and
--     excluding (true) variants of an outlier trade-off (Req 7).
--   * created_at / updated_at support meeting history retention (Req 9.6).
--   * per_participant_times is JSONB mapping participantId -> whole minutes.

CREATE TABLE meeting (
    id             UUID PRIMARY KEY,
    url_code       VARCHAR(64)              NOT NULL,
    transport_mode VARCHAR(16)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_meeting_url_code UNIQUE (url_code),
    CONSTRAINT ck_meeting_transport_mode CHECK (transport_mode IN ('driving', 'walking'))
);

CREATE TABLE participant (
    id         UUID PRIMARY KEY,
    meeting_id UUID             NOT NULL,
    name       VARCHAR(255)     NOT NULL,
    lat        DOUBLE PRECISION NOT NULL,
    lng        DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_participant_meeting FOREIGN KEY (meeting_id)
        REFERENCES meeting (id) ON DELETE CASCADE,
    CONSTRAINT ck_participant_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_participant_lng CHECK (lng BETWEEN -180 AND 180)
);

CREATE INDEX idx_participant_meeting_id ON participant (meeting_id);

CREATE TABLE recommendation (
    id                    UUID PRIMARY KEY,
    meeting_id            UUID             NOT NULL,
    strategy              VARCHAR(16)      NOT NULL,
    excludes_outlier      BOOLEAN          NOT NULL,
    point_lat             DOUBLE PRECISION NOT NULL,
    point_lng             DOUBLE PRECISION NOT NULL,
    sum_time              DOUBLE PRECISION NOT NULL,
    max_time              INTEGER          NOT NULL,
    std_dev               DOUBLE PRECISION NOT NULL,
    per_participant_times JSONB            NOT NULL,
    CONSTRAINT fk_recommendation_meeting FOREIGN KEY (meeting_id)
        REFERENCES meeting (id) ON DELETE CASCADE,
    CONSTRAINT ck_recommendation_strategy CHECK (strategy IN ('FASTEST', 'MINIMAX', 'FAIREST')),
    CONSTRAINT ck_recommendation_point_lat CHECK (point_lat BETWEEN -90 AND 90),
    CONSTRAINT ck_recommendation_point_lng CHECK (point_lng BETWEEN -180 AND 180)
);

CREATE INDEX idx_recommendation_meeting_id ON recommendation (meeting_id);
