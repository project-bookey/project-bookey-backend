-- 사용자 차단(2026-10-05) — 한 방향. 막은 사람에게는 막힌 사람이 엽서·채팅을 보낼 수 없고,
-- 막은 사람의 엽서함·채팅 목록에서 그 사람과의 엽서·채팅방이 빠진다(지우지 않아 풀면 다시 보인다).
-- 막힌 사람에게는 알리지 않는다.
CREATE TABLE user_blocks (
    id          BIGSERIAL PRIMARY KEY,
    blocker_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);
