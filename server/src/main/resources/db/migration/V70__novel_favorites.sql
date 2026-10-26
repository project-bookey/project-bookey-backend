-- 소설 즐겨찾기 — 좋아요(novel_likes)와 따로 둔다(2026-10-26 사용자 결정). user_id+novel_id 당 1건.
-- 마이의 즐겨찾기 목록이 내가 담은 순(최근 먼저)으로 읽는다.
-- V69 는 다른 작업(내가 이어 쓴 이야기)이 쓴다.
CREATE TABLE novel_favorites (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    novel_id   BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, novel_id)
);
CREATE INDEX idx_novel_favorites_user_recent ON novel_favorites(user_id, created_at DESC);
