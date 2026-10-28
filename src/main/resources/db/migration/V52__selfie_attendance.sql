-- V52: selfie + GPS check-in.
-- Company attendance policy gains "require selfie at check-in" and a selfie
-- retention window (days); attendance_session stores the S3 key of the
-- check-in selfie (nulled once the nightly retention job deletes the object).

ALTER TABLE company ADD COLUMN IF NOT EXISTS require_selfie_check_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE company ADD COLUMN IF NOT EXISTS selfie_retention_days INTEGER NOT NULL DEFAULT 90;

ALTER TABLE attendance_session ADD COLUMN IF NOT EXISTS check_in_selfie_key VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_attendance_session_selfie_purge
    ON attendance_session (company_id, attendance_date)
    WHERE check_in_selfie_key IS NOT NULL;
