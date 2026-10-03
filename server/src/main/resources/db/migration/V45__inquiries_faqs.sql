-- 고객문의(1:1)와 FAQ. 문의 하나에 답변 하나 — 더 물을 게 있으면 새 문의로 받는다.
CREATE TABLE inquiries (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category          VARCHAR(20)  NOT NULL,              -- USAGE|ACCOUNT|BUG|PAYMENT|SUGGESTION|ETC
    body              TEXT         NOT NULL,
    app_version       VARCHAR(30),                        -- 앱이 자동으로 붙이는 기기 정보(웹·구버전은 빌 수 있다)
    platform          VARCHAR(20),                        -- IOS|ANDROID|WEB
    os_version        VARCHAR(30),
    device_model      VARCHAR(100),
    status            VARCHAR(20)  NOT NULL DEFAULT 'WAITING',  -- WAITING|ANSWERED
    answer            TEXT,
    answered_by       BIGINT       REFERENCES admins(id) ON DELETE SET NULL,  -- 마지막으로 답변을 저장한 관리자
    answered_at       TIMESTAMPTZ,                        -- 첫 답변 시각(고쳐도 그대로)
    answer_updated_at TIMESTAMPTZ,                        -- 마지막으로 고친 시각(고친 적 없으면 NULL)
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inquiries_user ON inquiries(user_id, id DESC);
CREATE INDEX idx_inquiries_status ON inquiries(status, created_at);

-- 문의 첨부 사진. 올릴 때는 inquiry_id 가 비어 있고(임시) 문의를 만들 때 붙인다.
-- 문의를 지우면 FK 가 inquiry_id 를 비워 고아가 되고, 정리 배치가 파일과 행을 회수한다.
CREATE TABLE inquiry_images (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    inquiry_id   BIGINT       REFERENCES inquiries(id) ON DELETE SET NULL,
    sort_order   SMALLINT     NOT NULL DEFAULT 0,
    storage_key  VARCHAR(255) NOT NULL UNIQUE,
    url          TEXT         NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    byte_size    INT          NOT NULL,
    width        INT,
    height       INT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inquiry_images_inquiry ON inquiry_images(inquiry_id, sort_order, id);
CREATE INDEX idx_inquiry_images_orphan ON inquiry_images(updated_at) WHERE inquiry_id IS NULL;

-- 자주 묻는 질문. 분류는 문의 유형과 같은 값을 쓰고, 순서는 전역 하나(sort_order)로 관리한다.
CREATE TABLE faqs (
    id         BIGSERIAL PRIMARY KEY,
    category   VARCHAR(20)  NOT NULL,
    question   VARCHAR(200) NOT NULL,
    answer     TEXT         NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    visible    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
