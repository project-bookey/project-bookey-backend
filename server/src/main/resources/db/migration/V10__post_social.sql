-- 독후감 소셜 — 사진·밑줄 연결·좋아요·댓글(2단계). 기존 posts 테이블에 덧붙인다.

-- 업로드 시에는 post_id 가 NULL(임시), 독후감에 붙일 때 post_id·sort_order 를 채운다.
CREATE TABLE post_images (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id      BIGINT REFERENCES posts(id) ON DELETE CASCADE,
    storage_key  VARCHAR(255) NOT NULL UNIQUE,   -- posts/{userId}/yyyy/MM/{uuid}.{ext}
    url          TEXT NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    byte_size    INT NOT NULL,
    width        INT,
    height       INT,
    sort_order   SMALLINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_images_post ON post_images(post_id, sort_order, id);
-- 붙지 못하고 남은 임시 업로드 정리(배치)용 부분 인덱스.
CREATE INDEX idx_post_images_orphan ON post_images(created_at) WHERE post_id IS NULL;

-- 독후감에 인용한 밑줄 — 같은 밑줄은 한 번만.
CREATE TABLE post_quotes (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    quote_id   BIGINT NOT NULL REFERENCES book_quotes(id) ON DELETE CASCADE,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, quote_id)
);
CREATE INDEX idx_post_quotes_post ON post_quotes(post_id, sort_order, id);
CREATE INDEX idx_post_quotes_quote ON post_quotes(quote_id);

-- 독후감 좋아요 — quote_agrees 미러. user_id+post_id 당 1건.
CREATE TABLE post_likes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, post_id)
);
CREATE INDEX idx_post_likes_post ON post_likes(post_id);

-- 독후감 댓글 — parent_id 자기참조로 2단계(루트 + 답글)까지만.
CREATE TABLE post_comments (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id  BIGINT REFERENCES post_comments(id) ON DELETE CASCADE,
    body       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 루트 목록은 오래된 순 — 같은 시각이면 id 로 안정 정렬한다.
CREATE INDEX idx_post_comments_root ON post_comments(post_id, created_at, id) WHERE parent_id IS NULL;
CREATE INDEX idx_post_comments_post ON post_comments(post_id);
CREATE INDEX idx_post_comments_parent ON post_comments(parent_id, created_at, id);

-- 책 상세의 공개 독후감 목록용.
CREATE INDEX idx_posts_book_public ON posts(book_id, published_at DESC, id DESC) WHERE visibility = 'PUBLIC';
