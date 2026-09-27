CREATE TABLE club_chat_unlocks (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cost_bookmarks INT NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_id, user_id)
);

CREATE TABLE club_chat_messages (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_chat_messages_club ON club_chat_messages(club_id, id DESC);

CREATE TABLE club_chat_reads (
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (club_id, user_id)
);

CREATE TABLE club_meetings (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    place_name VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    map_url TEXT,
    response_deadline TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_club_meetings_club ON club_meetings(club_id, starts_at);

CREATE TABLE club_meeting_attendees (
    meeting_id BIGINT NOT NULL REFERENCES club_meetings(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    responded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (meeting_id, user_id)
);
