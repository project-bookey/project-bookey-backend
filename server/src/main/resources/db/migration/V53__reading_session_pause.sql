-- 독서 타이머 잠깐 쉬기 — 쉬기 시작한 시각(쉬는 중일 때만)과 지금까지 쉰 시간(초).
-- 독서 시간(duration_sec)은 끝난 시각 − 시작 시각에서 쉰 시간을 뺀 값이 된다.
ALTER TABLE reading_sessions
    ADD COLUMN paused_at  TIMESTAMPTZ,
    ADD COLUMN paused_sec INTEGER NOT NULL DEFAULT 0;
