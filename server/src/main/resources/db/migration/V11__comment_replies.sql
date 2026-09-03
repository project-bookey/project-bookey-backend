-- 댓글 1단계 답글(대댓글) + 리뷰 댓글. 설계: project-bookey-app/docs/superpowers/specs/2026-09-03-review-comments-design.md
-- 1) 밑줄 댓글에 1단계 답글. parent_id 가 있으면 답글, 없으면 최상위. 부모를 지우면 답글도 함께 사라진다.
ALTER TABLE quote_comments
    ADD COLUMN parent_id BIGINT REFERENCES quote_comments(id) ON DELETE CASCADE;
-- 답글 목록: 부모별 오래된 순. 최상위 행(NULL)은 색인에서 뺀다.
CREATE INDEX idx_quote_comments_parent
    ON quote_comments(parent_id, created_at, id) WHERE parent_id IS NOT NULL;

-- 2) 리뷰 댓글 — quote_comments 와 같은 모양(본문 300자, 1단계 답글).
-- 리뷰는 소프트 삭제(status)라 review_id ... ON DELETE CASCADE 는 하드 삭제 때만 발동한다.
-- 지워진 리뷰의 댓글은 테이블에 남지만 API 로는 닿을 수 없다 — 모든 조회 경로가 isVisible 로 거른다.
CREATE TABLE review_comments (
    id         BIGSERIAL PRIMARY KEY,
    review_id  BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id  BIGINT REFERENCES review_comments(id) ON DELETE CASCADE,
    body       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 최상위 목록(오래된 순) + commentCount 집계(GROUP BY review_id)를 함께 태운다.
CREATE INDEX idx_review_comments_review ON review_comments(review_id, created_at, id);
CREATE INDEX idx_review_comments_parent
    ON review_comments(parent_id, created_at, id) WHERE parent_id IS NOT NULL;
