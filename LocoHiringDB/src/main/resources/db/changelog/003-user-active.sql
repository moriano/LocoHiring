--liquibase formatted sql

--changeset locohiring:003-user-active
-- Deactivated users cannot log in, and deactivated interviewers cannot be booked for interviews.
ALTER TABLE recruiters ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE interviewers ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
