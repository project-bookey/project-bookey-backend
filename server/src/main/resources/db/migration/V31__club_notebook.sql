-- 모임 노트북 — 모임당 노트 한 권, 순서 있는 페이지. 활성 멤버 누구나 읽고 고친다.
-- document 는 앱이 소유한 JSON 이라 서버는 크기·요소 수만 검사하고 내용은 해석하지 않는다.
CREATE TABLE club_note_pages (
    id            BIGSERIAL PRIMARY KEY,
    club_id       BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    seq           INT         NOT NULL,                 -- 정렬 키. 지우면 빈 번호는 그대로 둔다
    title         VARCHAR(60),
    document      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    version       INT         NOT NULL DEFAULT 0,       -- 낙관적 덮어쓰기용. 저장마다 +1
    element_count INT         NOT NULL DEFAULT 0,       -- document.elements 길이 — 목록에서 문서 없이 보여주려고 따로 둔다
    created_by    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    updated_by    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_id, seq)
);

-- 노트북 사진. 업로드 시에는 page_id 가 NULL(임시)이고, 그 사진을 참조하는 문서가 저장될 때 page_id 가 채워진다.
-- 문서에서 빠지면 page_id 를 다시 비우고, 24시간 안에 어느 문서에도 붙지 않은 사진은 배치가 파일과 행을 지운다.
CREATE TABLE club_note_images (
    id           BIGSERIAL PRIMARY KEY,
    club_id      BIGINT       NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    page_id      BIGINT       REFERENCES club_note_pages(id) ON DELETE SET NULL,
    storage_key  VARCHAR(255) NOT NULL UNIQUE,
    url          TEXT         NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    byte_size    INT          NOT NULL,
    width        INT,
    height       INT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_note_images_page ON club_note_images(page_id);
CREATE INDEX idx_club_note_images_orphan ON club_note_images(updated_at) WHERE page_id IS NULL;
