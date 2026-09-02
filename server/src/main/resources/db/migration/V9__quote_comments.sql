-- 밑줄에 덧붙인 말(댓글). 설계: project-bookey-app/docs/superpowers/specs/2026-09-02-quote-comments-design.md
CREATE TABLE quote_comments (
    id         BIGSERIAL PRIMARY KEY,
    quote_id   BIGINT NOT NULL REFERENCES book_quotes(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 목록은 오래된 순 — 같은 시각이면 id 로 안정 정렬한다.
CREATE INDEX idx_quote_comments_quote ON quote_comments(quote_id, created_at, id);
