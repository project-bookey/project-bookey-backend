-- ============================================================
--  bookey V1 — 코어 스키마 (users / books / reading / goals)
--  기획서 §7 데이터 모델
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── 사용자 ───────────────────────────────────────────────────
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    handle              VARCHAR(30)  NOT NULL UNIQUE,
    email               VARCHAR(255) UNIQUE,
    nickname            VARCHAR(50)  NOT NULL,
    avatar_url          TEXT,
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'Asia/Seoul',
    -- 알림 설정 (§F5)
    notify_tone         VARCHAR(20)  NOT NULL DEFAULT 'GENTLE',   -- GENTLE|FACT|SPARTA|TSUNDERE|SILENT
    quiet_hours_start   SMALLINT     NOT NULL DEFAULT 22,
    quiet_hours_end     SMALLINT     NOT NULL DEFAULT 8,
    daily_notify_cap    SMALLINT     NOT NULL DEFAULT 2,
    club_notify_cap     SMALLINT     NOT NULL DEFAULT 3,
    allow_nudge         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 상태
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE|WRITE_BANNED|SUSPENDED|TERMINATED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 소셜 로그인 자격 (Apple/Google/Kakao)
CREATE TABLE user_identities (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      VARCHAR(20)  NOT NULL,   -- APPLE|GOOGLE|KAKAO|DEV
    provider_uid  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_uid)
);
CREATE INDEX idx_user_identities_user ON user_identities(user_id);

-- 디바이스 푸시 토큰
CREATE TABLE user_devices (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform      VARCHAR(10)  NOT NULL,   -- IOS|ANDROID
    push_token    TEXT         NOT NULL,
    push_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (platform, push_token)
);
CREATE INDEX idx_user_devices_user ON user_devices(user_id);

-- 리프레시 토큰
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ── 도서 (§F1) ──────────────────────────────────────────────
CREATE TABLE books (
    id              BIGSERIAL PRIMARY KEY,
    isbn13          VARCHAR(13) UNIQUE,
    title           VARCHAR(500) NOT NULL,
    subtitle        VARCHAR(500),
    author          VARCHAR(500),
    translator      VARCHAR(255),
    publisher       VARCHAR(255),
    published_at    DATE,
    total_pages     INT,                         -- 진척도 계산의 핵심. NULL 허용(사용자 입력 유도)
    cover_url       TEXT,
    category        VARCHAR(255),
    genre_key       VARCHAR(30) NOT NULL DEFAULT 'GENERAL', -- 최소 독서시간 계수(§F6)
    description     TEXT,
    source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- KAKAO|ALADIN|GOOGLE|NAVER|MANUAL
    source_raw      JSONB,
    is_user_created BOOLEAN NOT NULL DEFAULT FALSE,
    meta_enriched_at TIMESTAMPTZ,                -- 알라딘 보강 완료 시각
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_author ON books(author);
CREATE INDEX idx_books_enrich ON books(meta_enriched_at) WHERE meta_enriched_at IS NULL;

-- 페이지 수 크라우드 입력(다수결 채택, §12 리스크 대응)
CREATE TABLE book_page_suggestions (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    total_pages INT    NOT NULL CHECK (total_pages > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (book_id, user_id)
);

-- ── 독서 기록 (§F2) ─────────────────────────────────────────
CREATE TABLE reading_records (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id             BIGINT      NOT NULL REFERENCES books(id),
    round               SMALLINT    NOT NULL DEFAULT 1,   -- 재독 회차
    status              VARCHAR(20) NOT NULL DEFAULT 'WANT_TO_READ',
                        -- WANT_TO_READ|READING|PAUSED|FINISHED|ABANDONED
    current_page        INT         NOT NULL DEFAULT 0,
    total_pages_override INT,
    target_finish_date  DATE,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    last_read_at        TIMESTAMPTZ,
    abandon_reason      VARCHAR(30),   -- BORING|DIFFICULT|NO_TIME|NOT_MY_TASTE|OTHER
    rating              SMALLINT CHECK (rating BETWEEN 1 AND 5),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, book_id, round)
);
CREATE INDEX idx_reading_records_user_status ON reading_records(user_id, status);
CREATE INDEX idx_reading_records_lag ON reading_records(status, last_read_at)
    WHERE status = 'READING';

-- ── 독서 세션 (§F3) ─────────────────────────────────────────
CREATE TABLE reading_sessions (
    id                BIGSERIAL PRIMARY KEY,
    reading_record_id BIGINT      NOT NULL REFERENCES reading_records(id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    duration_sec      INT         NOT NULL DEFAULT 0,
    start_page        INT,
    end_page          INT,
    source            VARCHAR(10) NOT NULL DEFAULT 'TIMER',   -- TIMER|MANUAL
    foreground_ratio  NUMERIC(4,3),
    interaction_count INT         NOT NULL DEFAULT 0,
    memo              TEXT,
    abuse_flags       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    counted_for_verification BOOLEAN NOT NULL DEFAULT TRUE,
    client_uuid       UUID,                                   -- 오프라인 동기화 멱등키
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, client_uuid)
);
CREATE INDEX idx_sessions_user_started ON reading_sessions(user_id, started_at DESC);
CREATE INDEX idx_sessions_record ON reading_sessions(reading_record_id, started_at DESC);
CREATE INDEX idx_sessions_open ON reading_sessions(user_id) WHERE ended_at IS NULL;

-- ── 목표 (§F4) ──────────────────────────────────────────────
CREATE TABLE goals (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(10) NOT NULL,   -- BOOK|WEEKLY|YEARLY
    reading_record_id BIGINT REFERENCES reading_records(id) ON DELETE CASCADE,
    target_value INT         NOT NULL,
    unit         VARCHAR(10) NOT NULL,   -- PAGES|MINUTES|SESSIONS|BOOKS
    period_start DATE        NOT NULL,
    period_end   DATE        NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|ACHIEVED|EXPIRED|CANCELED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user_active ON goals(user_id, status, period_end);

-- ── 리뷰 (§F6) ──────────────────────────────────────────────
CREATE TABLE reviews (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id               BIGINT      NOT NULL REFERENCES books(id),
    reading_record_id     BIGINT      REFERENCES reading_records(id) ON DELETE SET NULL,
    rating                SMALLINT    CHECK (rating BETWEEN 1 AND 5),
    body                  TEXT        NOT NULL,
    tags                  TEXT[]      NOT NULL DEFAULT '{}',
    has_spoiler           BOOLEAN     NOT NULL DEFAULT FALSE,
    verification_level    VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
                          -- VERIFIED_FULL|VERIFIED_PARTIAL|UNVERIFIED|FLAGGED
    verification_snapshot JSONB,
    helpful_count         INT         NOT NULL DEFAULT 0,
    report_count          INT         NOT NULL DEFAULT 0,
    status                VARCHAR(10) NOT NULL DEFAULT 'VISIBLE', -- VISIBLE|HIDDEN|DELETED
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, reading_record_id)
);
CREATE INDEX idx_reviews_book_rank ON reviews(book_id, verification_level, helpful_count DESC);

-- ── 독후감 (§F7) ────────────────────────────────────────────
CREATE TABLE posts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id           BIGINT      REFERENCES books(id),
    reading_record_id BIGINT      REFERENCES reading_records(id) ON DELETE SET NULL,
    slug              VARCHAR(120) NOT NULL,
    title             VARCHAR(300) NOT NULL,
    body_md           TEXT        NOT NULL,
    visibility        VARCHAR(10) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC|LINK|PRIVATE
    tags              TEXT[]      NOT NULL DEFAULT '{}',
    series_id         BIGINT,
    published_at      TIMESTAMPTZ,
    view_count        INT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, slug)
);
CREATE INDEX idx_posts_public ON posts(visibility, published_at DESC);
CREATE INDEX idx_posts_user ON posts(user_id, published_at DESC);

-- ── 인용 (§F11) ─────────────────────────────────────────────
CREATE TABLE quotes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    BIGINT NOT NULL REFERENCES books(id),
    page       INT,
    content    TEXT   NOT NULL,
    image_url  TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quotes_user_book ON quotes(user_id, book_id);

-- ── 공유 카드 (§F8) ─────────────────────────────────────────
CREATE TABLE share_cards (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id           BIGINT      REFERENCES books(id),
    reading_record_id BIGINT      REFERENCES reading_records(id) ON DELETE SET NULL,
    club_id           BIGINT,     -- V2에서 FK 연결
    type              VARCHAR(15) NOT NULL, -- DAILY|FINISH|WEEKLY|MONTHLY|YEARLY|QUOTE|CHALLENGE|CLUB
    template_key      VARCHAR(40) NOT NULL DEFAULT 'MINIMAL',
    aspect_ratio      VARCHAR(10) NOT NULL DEFAULT '4:5',
    title             VARCHAR(200),
    payload           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    image_url         TEXT,
    visibility        VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    shared_at         TIMESTAMPTZ
);
CREATE INDEX idx_share_cards_user ON share_cards(user_id, type, created_at DESC);

-- ── 알림 (§F5) ──────────────────────────────────────────────
CREATE TABLE notifications (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type               VARCHAR(30) NOT NULL,
                       -- HABIT|LAG|MICRO_MISSION|STREAK|ALMOST_DONE|ACHIEVEMENT|CLEANUP
                       -- CLUB_CHECKPOINT_DUE|CLUB_CHECKPOINT_RESULT|CLUB_OVERTAKEN
                       -- CLUB_FALLBEHIND|CLUB_NEW_POST|CLUB_NUDGE|CLUB_ENDED
    lag_level          SMALLINT,
    reading_record_id  BIGINT REFERENCES reading_records(id) ON DELETE CASCADE,
    club_id            BIGINT,     -- V2에서 FK 연결
    title              VARCHAR(200) NOT NULL,
    body               VARCHAR(500) NOT NULL,
    payload            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    scheduled_at       TIMESTAMPTZ NOT NULL,
    sent_at            TIMESTAMPTZ,
    opened_at          TIMESTAMPTZ,
    converted_at       TIMESTAMPTZ,
    experiment_variant VARCHAR(40),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_pending ON notifications(scheduled_at) WHERE sent_at IS NULL;
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

-- ── 신고 (§8.3) ─────────────────────────────────────────────
CREATE TABLE abuse_reports (
    id          BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,   -- REVIEW|POST|CLUB_POST|CLUB|USER
    target_id   BIGINT      NOT NULL,
    reporter_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason      VARCHAR(30) NOT NULL,
    detail      TEXT,
    status      VARCHAR(15) NOT NULL DEFAULT 'PENDING', -- PENDING|RESOLVED|REJECTED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (target_type, target_id, reporter_id)
);
CREATE INDEX idx_abuse_reports_status ON abuse_reports(status, created_at);
