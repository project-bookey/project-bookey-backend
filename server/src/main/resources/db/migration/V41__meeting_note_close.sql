-- 모임 노트 마무리 — 모임을 연 사람(또는 호스트)이 마무리하면 노트는 읽기만 된다.
ALTER TABLE club_meeting_notes ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE club_meeting_notes ADD COLUMN closed_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
