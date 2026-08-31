-- ============================================================
--  bookey V2 — 독서 모임 (F12)
-- ============================================================

CREATE TABLE clubs (
    id            BIGSERIAL PRIMARY KEY,
    owner_id      BIGINT      NOT NULL REFERENCES users(id),
    name          VARCHAR(60) NOT NULL,
    description   VARCHAR(1000),
    cover_url     TEXT,
    join_code     VARCHAR(6)  NOT NULL UNIQUE,   -- base32, 혼동문자 제외
    visibility    VARCHAR(12) NOT NULL DEFAULT 'CODE_ONLY',  -- CODE_ONLY|LINK|PUBLIC
    status        VARCHAR(12) NOT NULL DEFAULT 'RECRUITING', -- RECRUITING|ACTIVE|ENDED|ARCHIVED
    member_limit  SMALLINT    NOT NULL DEFAULT 20 CHECK (member_limit BETWEEN 2 AND 50),
    member_count  SMALLINT    NOT NULL DEFAULT 0,
    starts_at     DATE        NOT NULL,
    ends_at       DATE        NOT NULL,
    ended_at      TIMESTAMPTZ,
    allow_nudge   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at >= starts_at)
);
CREATE INDEX idx_clubs_owner ON clubs(owner_id);
CREATE INDEX idx_clubs_public ON clubs(visibility, status, created_at DESC)
    WHERE visibility = 'PUBLIC';

-- 모임 선정 도서 (MVP는 1권, 시즌제 대비 seq 보유)
CREATE TABLE club_books (
    id                  BIGSERIAL PRIMARY KEY,
    club_id             BIGINT   NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    book_id             BIGINT   NOT NULL REFERENCES books(id),
    seq                 SMALLINT NOT NULL DEFAULT 1,
    target_finish_date  DATE,
    total_pages_snapshot INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_id, seq)
);

CREATE TABLE club_members (
    id                BIGSERIAL PRIMARY KEY,
    club_id           BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reading_record_id BIGINT      REFERENCES reading_records(id) ON DELETE SET NULL,
    role              VARCHAR(10) NOT NULL DEFAULT 'MEMBER',  -- HOST|MODERATOR|MEMBER
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|LEFT|KICKED
    share_progress    BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_nudge       BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at           TIMESTAMPTZ,
    last_read_at      TIMESTAMPTZ,
    kick_reason       VARCHAR(200),
    UNIQUE (club_id, user_id)
);
CREATE INDEX idx_club_members_club ON club_members(club_id, status);
CREATE INDEX idx_club_members_user ON club_members(user_id, status);

-- 주차별 체크포인트
CREATE TABLE club_checkpoints (
    id           BIGSERIAL PRIMARY KEY,
    club_book_id BIGINT      NOT NULL REFERENCES club_books(id) ON DELETE CASCADE,
    seq          SMALLINT    NOT NULL,
    title        VARCHAR(60) NOT NULL,
    target_page  INT         NOT NULL CHECK (target_page > 0),
    due_at       TIMESTAMPTZ NOT NULL,
    evaluated_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_book_id, seq)
);
CREATE INDEX idx_club_checkpoints_due ON club_checkpoints(due_at) WHERE evaluated_at IS NULL;

CREATE TABLE club_checkpoint_progress (
    id             BIGSERIAL PRIMARY KEY,
    checkpoint_id  BIGINT      NOT NULL REFERENCES club_checkpoints(id) ON DELETE CASCADE,
    club_member_id BIGINT      NOT NULL REFERENCES club_members(id) ON DELETE CASCADE,
    page_at_due    INT         NOT NULL DEFAULT 0,
    achieved       BOOLEAN     NOT NULL DEFAULT FALSE,
    achieved_at    TIMESTAMPTZ,
    evaluated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (checkpoint_id, club_member_id)
);
CREATE INDEX idx_ckpt_progress_ckpt ON club_checkpoint_progress(checkpoint_id);

-- 토론 (페이지 앵커 + 스포일러 가드)
CREATE TABLE club_posts (
    id             BIGSERIAL PRIMARY KEY,
    club_id        BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    club_book_id   BIGINT      REFERENCES club_books(id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id      BIGINT      REFERENCES club_posts(id) ON DELETE CASCADE,
    type           VARCHAR(12) NOT NULL DEFAULT 'DISCUSSION',
                   -- DISCUSSION|QUESTION|QUOTE|NOTICE|CHECKPOINT
    body           TEXT        NOT NULL,
    anchor_page    INT,                                   -- 스포일러 기준 페이지
    spoiler_level  VARCHAR(6)  NOT NULL DEFAULT 'PAGE',   -- NONE|PAGE|BOOK
    linked_post_id BIGINT      REFERENCES posts(id) ON DELETE SET NULL,
    is_pinned      BOOLEAN     NOT NULL DEFAULT FALSE,
    comment_count  INT         NOT NULL DEFAULT 0,
    reaction_count INT         NOT NULL DEFAULT 0,
    report_count   INT         NOT NULL DEFAULT 0,
    status         VARCHAR(10) NOT NULL DEFAULT 'VISIBLE', -- VISIBLE|HIDDEN|DELETED
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_posts_feed ON club_posts(club_id, created_at DESC)
    WHERE parent_id IS NULL;
CREATE INDEX idx_club_posts_anchor ON club_posts(club_id, anchor_page);
CREATE INDEX idx_club_posts_parent ON club_posts(parent_id, created_at);

CREATE TABLE club_post_reactions (
    id           BIGSERIAL PRIMARY KEY,
    club_post_id BIGINT      NOT NULL REFERENCES club_posts(id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(6)  NOT NULL,   -- LIKE|FIRE|CRY|THINK
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_post_id, user_id, kind)
);

-- 스포일러 해제 감사 로그 (§8.5)
CREATE TABLE club_post_reveals (
    id           BIGSERIAL PRIMARY KEY,
    club_post_id BIGINT      NOT NULL REFERENCES club_posts(id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    page_at_reveal INT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_post_id, user_id)
);

-- 찌르기 (프리셋 문구만, 횟수 제한)
CREATE TABLE club_nudges (
    id           BIGSERIAL PRIMARY KEY,
    club_id      BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    from_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_key  VARCHAR(30) NOT NULL,   -- READ_TOGETHER|CHECKPOINT_SOON|WAITING
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_user_id <> to_user_id)
);
CREATE INDEX idx_club_nudges_quota ON club_nudges(from_user_id, to_user_id, created_at DESC);
CREATE INDEX idx_club_nudges_daily ON club_nudges(from_user_id, created_at DESC);

-- 모임 활동 피드
CREATE TABLE club_events (
    id         BIGSERIAL PRIMARY KEY,
    club_id    BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    type       VARCHAR(20) NOT NULL,
               -- CREATED|JOINED|LEFT|KICKED|FINISHED|CHECKPOINT_MET|CHECKPOINT_MISSED|POSTED|ENDED
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_events_club ON club_events(club_id, created_at DESC);

-- V1에서 예약해둔 club_id FK 연결
ALTER TABLE share_cards   ADD CONSTRAINT fk_share_cards_club
    FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE SET NULL;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_club
    FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE;
