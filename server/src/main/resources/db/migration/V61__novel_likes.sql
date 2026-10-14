-- 소설 좋아요 — 책(book_likes)·독후감(post_likes)과 따로 둔다. user_id+novel_id 당 1건.
CREATE TABLE novel_likes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    novel_id   BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, novel_id)
);
CREATE INDEX idx_novel_likes_novel ON novel_likes(novel_id);
