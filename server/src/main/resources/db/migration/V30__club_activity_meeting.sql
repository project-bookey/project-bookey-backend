ALTER TABLE club_activity_sessions ADD COLUMN meeting_id BIGINT REFERENCES club_meetings(id) ON DELETE SET NULL;
CREATE INDEX idx_club_activity_sessions_meeting ON club_activity_sessions(meeting_id, started_at DESC);
