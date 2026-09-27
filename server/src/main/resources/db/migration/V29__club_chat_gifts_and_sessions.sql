ALTER TABLE club_chat_unlocks ADD COLUMN paid_by_user_id BIGINT REFERENCES users(id);
UPDATE club_chat_unlocks SET paid_by_user_id = user_id WHERE paid_by_user_id IS NULL;
ALTER TABLE club_chat_unlocks ALTER COLUMN paid_by_user_id SET NOT NULL;

CREATE TABLE club_activity_sessions (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_sec INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_club_activity_running ON club_activity_sessions(club_id, user_id) WHERE ended_at IS NULL;

CREATE TABLE club_activity_cards (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL UNIQUE REFERENCES club_activity_sessions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caption VARCHAR(500),
    decorations_json TEXT NOT NULL DEFAULT '[]',
    photo_url TEXT,
    photo_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_activity_cards_club ON club_activity_cards(club_id, id DESC);
