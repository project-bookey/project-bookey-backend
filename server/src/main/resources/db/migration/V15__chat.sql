-- ============================================================
--  bookey V15 — 1:1 채팅 (기획서 §14.3)
--  맞팔로우끼리만. 채팅방은 사용자 쌍당 1개(a_user_id < b_user_id 정규화).
-- ============================================================

CREATE TABLE chats (
    id               BIGSERIAL PRIMARY KEY,
    a_user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    b_user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    a_last_read_at   TIMESTAMPTZ,             -- a 가 마지막으로 읽은 시각 (안읽음 계산)
    b_last_read_at   TIMESTAMPTZ,
    last_message_at  TIMESTAMPTZ,             -- 목록 정렬용 — 메시지 없으면 created_at 사용
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CHECK (a_user_id < b_user_id),
    UNIQUE (a_user_id, b_user_id)
);
CREATE INDEX idx_chats_a ON chats(a_user_id);
CREATE INDEX idx_chats_b ON chats(b_user_id);

CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    chat_id     BIGINT        NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id   BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX idx_chat_messages_chat ON chat_messages(chat_id, id DESC);
