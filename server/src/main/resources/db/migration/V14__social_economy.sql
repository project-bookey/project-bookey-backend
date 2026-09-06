-- ============================================================
--  bookey V14 — 소셜 피드 경제 (기획서 §14, v1.2)
--  팔로우(코드·엽서) / 엽서·우표 / 책갈피 지갑·원장 / 구독 / 방문 기록
-- ============================================================

-- 팔로우용 공개 코드 — 16자리, QR·직접 입력 공유 (§14.3)
CREATE TABLE user_public_ids (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code        VARCHAR(16) NOT NULL UNIQUE,
    rotated_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

-- 팔로우 — 경로는 상호 엽서(POSTCARD) 또는 코드(CODE)뿐 (§14.3)
CREATE TABLE user_follows (
    id           BIGSERIAL PRIMARY KEY,
    follower_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source       VARCHAR(10) NOT NULL,   -- POSTCARD|CODE
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    UNIQUE (follower_id, followee_id)
);
CREATE INDEX idx_user_follows_followee ON user_follows(followee_id, id DESC);

-- 엽서 — 16글자(grapheme), 답장 성립 시 자동 맞팔로우 (§14.2)
CREATE TABLE postcards (
    id              BIGSERIAL PRIMARY KEY,
    from_user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id         BIGINT       REFERENCES posts(id) ON DELETE SET NULL,  -- 어떤 독후감을 보고 보냈나
    body            VARCHAR(128) NOT NULL,   -- 16 grapheme — 코드 유닛으로는 여유를 둔다(ZWJ 이모지)
    stamp_attached  BOOLEAN      NOT NULL DEFAULT FALSE,  -- 우표 동봉: 수신자가 무료로 답장
    status          VARCHAR(10)  NOT NULL,   -- SENT|REPLIED
    reply_body      VARCHAR(128),
    replied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_postcards_to   ON postcards(to_user_id, id DESC);
CREATE INDEX idx_postcards_from ON postcards(from_user_id, id DESC);

-- 지갑 — 책갈피(기축) · 엽서 · 우표. 잔액은 원장 합계의 캐시다 (§14.2)
CREATE TABLE wallets (
    id                         BIGSERIAL PRIMARY KEY,
    user_id                    BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    bookmark_balance           INT         NOT NULL DEFAULT 0,
    postcard_balance           INT         NOT NULL DEFAULT 0,
    stamp_balance              INT         NOT NULL DEFAULT 0,
    free_postcards_used_today  SMALLINT    NOT NULL DEFAULT 0,
    free_reset_date            DATE        NOT NULL,    -- KST 기준 날짜 — 자정 리셋
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ
);

-- 재화 원장 — 모든 증감은 여기에 남는다
CREATE TABLE wallet_transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind            VARCHAR(30) NOT NULL,
    bookmark_delta  INT         NOT NULL DEFAULT 0,
    postcard_delta  INT         NOT NULL DEFAULT 0,
    stamp_delta     INT         NOT NULL DEFAULT 0,
    ref_type        VARCHAR(20),
    ref_id          BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_wallet_tx_user ON wallet_transactions(user_id, id DESC);

-- 구독 — 월 17,900원. MVP 는 관리자 지급(ADMIN), 스토어 IAP 검증은 후속 (§14.2)
CREATE TABLE subscriptions (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store                    VARCHAR(10)  NOT NULL,   -- APPLE|GOOGLE|ADMIN
    product_id               VARCHAR(100) NOT NULL,
    status                   VARCHAR(10)  NOT NULL,   -- ACTIVE|EXPIRED|CANCELLED
    current_period_start     TIMESTAMPTZ  NOT NULL,
    current_period_end       TIMESTAMPTZ  NOT NULL,
    last_grant_period_start  TIMESTAMPTZ,             -- 월 재화 지급 멱등 키
    original_transaction_id  VARCHAR(200),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ
);
CREATE INDEX idx_subscriptions_user ON subscriptions(user_id, id DESC);

-- 마이페이지 방문 기록 — 방문자·호스트·KST 날짜당 1건 (§14.2 구독 열람권)
CREATE TABLE profile_visits (
    id          BIGSERIAL PRIMARY KEY,
    visitor_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    host_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    visit_date  DATE        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    UNIQUE (visitor_id, host_id, visit_date)
);
CREATE INDEX idx_profile_visits_host ON profile_visits(host_id, id DESC);
