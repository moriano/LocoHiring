--liquibase formatted sql

--changeset locohiring:001-schema
CREATE TABLE job_families (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE positions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         TEXT    NOT NULL,
    description  TEXT,
    level        INTEGER NOT NULL CHECK (level > 0),
    jobfamily_id BIGINT  NOT NULL REFERENCES job_families (id)
);

CREATE TABLE candidates (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email               TEXT   NOT NULL UNIQUE,
    first_name          TEXT   NOT NULL,
    surname             TEXT,
    resume              BYTEA,
    resume_filename     TEXT,
    resume_content_type TEXT,
    position_id         BIGINT NOT NULL REFERENCES positions (id)
);

CREATE TABLE recruiters (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      TEXT    NOT NULL UNIQUE,
    password   TEXT    NOT NULL,
    first_name TEXT    NOT NULL,
    surname    TEXT,
    is_admin   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE interviewers (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      TEXT    NOT NULL UNIQUE,
    password   TEXT    NOT NULL,
    first_name TEXT    NOT NULL,
    surname    TEXT,
    level      INTEGER NOT NULL CHECK (level > 0),
    bar_raiser BOOLEAN NOT NULL DEFAULT FALSE,
    is_admin   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE interviewers_jobfamily (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    interviewer_id BIGINT NOT NULL REFERENCES interviewers (id),
    jobfamily_id   BIGINT NOT NULL REFERENCES job_families (id),
    UNIQUE (interviewer_id, jobfamily_id)
);

CREATE TABLE interview_types (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE funnels (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         TEXT   NOT NULL UNIQUE,
    description  TEXT,
    recruiter_id BIGINT NOT NULL REFERENCES recruiters (id)
);

-- One row per interview of the funnel; rows are ordered by id.
CREATE TABLE funnel_details (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funnel_id        BIGINT NOT NULL REFERENCES funnels (id),
    interviewtype_id BIGINT NOT NULL REFERENCES interview_types (id)
);

CREATE TABLE applications (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT      NOT NULL REFERENCES candidates (id),
    funnel_id    BIGINT      NOT NULL REFERENCES funnels (id),
    recruiter_id BIGINT      NOT NULL REFERENCES recruiters (id),
    status       TEXT        NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'HIRED', 'REJECTED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Interviews last exactly one hour, starting at scheduled_at.
CREATE TABLE interviews (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id   BIGINT      NOT NULL REFERENCES applications (id),
    interviewer_id   BIGINT REFERENCES interviewers (id),
    shadow_id        BIGINT REFERENCES interviewers (id),
    interviewtype_id BIGINT      NOT NULL REFERENCES interview_types (id),
    status           TEXT        NOT NULL DEFAULT 'LOOKING_FOR_INTERVIEWER'
        CHECK (status IN ('LOOKING_FOR_INTERVIEWER', 'SCHEDULED', 'COMPLETED', 'CANCELLED')),
    scheduled_at     TIMESTAMPTZ NOT NULL,
    CHECK (shadow_id IS NULL OR shadow_id <> interviewer_id)
);

-- One-hour availability blocks. interview_id is set when the block is booked, either by the
-- main interviewer or by the shadow of that interview.
CREATE TABLE calendar_availability (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    interviewer_id     BIGINT  NOT NULL REFERENCES interviewers (id),
    interview_id       BIGINT REFERENCES interviews (id),
    date               DATE    NOT NULL,
    start_availability INTEGER NOT NULL CHECK (start_availability BETWEEN 0 AND 23),
    end_availability   INTEGER NOT NULL,
    CHECK (end_availability = start_availability + 1),
    UNIQUE (interviewer_id, date, start_availability)
);

CREATE TABLE interview_feedback (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    interview_id   BIGINT      NOT NULL REFERENCES interviews (id),
    interviewer_id BIGINT      NOT NULL REFERENCES interviewers (id),
    feedback       TEXT        NOT NULL,
    inclined       BOOLEAN     NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (interview_id, interviewer_id)
);

-- A debrief is completed once notes and the inclined decision are recorded.
CREATE TABLE debriefs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT      NOT NULL UNIQUE REFERENCES applications (id),
    notes          TEXT,
    inclined       BOOLEAN,
    scheduled_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funnel_id      BIGINT REFERENCES funnels (id),
    application_id BIGINT REFERENCES applications (id),
    email          TEXT        NOT NULL,
    timestamp      TIMESTAMPTZ NOT NULL DEFAULT now(),
    message        TEXT        NOT NULL
);

CREATE INDEX idx_applications_candidate ON applications (candidate_id);
CREATE INDEX idx_applications_recruiter ON applications (recruiter_id);
CREATE INDEX idx_interviews_application ON interviews (application_id);
CREATE INDEX idx_interviews_interviewer ON interviews (interviewer_id);
CREATE INDEX idx_interviews_shadow ON interviews (shadow_id);
CREATE INDEX idx_calendar_availability_interview ON calendar_availability (interview_id);
CREATE INDEX idx_events_application ON events (application_id);
