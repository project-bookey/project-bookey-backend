CREATE TABLE daily_attendances (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attendance_date  DATE NOT NULL,
    streak_days      INTEGER NOT NULL,
    reward_bookmarks INTEGER NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_daily_attendance_user_date UNIQUE (user_id, attendance_date)
);

CREATE INDEX idx_daily_attendance_user_date
    ON daily_attendances(user_id, attendance_date DESC);
