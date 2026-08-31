-- ============================================================
--  bookey V3 — 관리자 백오피스 (F13)
--  사용자 계정과 완전히 분리된 인증 체계
-- ============================================================

CREATE TABLE admins (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    role           VARCHAR(15)  NOT NULL DEFAULT 'VIEWER',
                   -- SUPER_ADMIN|OPERATOR|SUPPORT|VIEWER
    totp_secret    VARCHAR(64),
    totp_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|SUSPENDED
    last_login_at  TIMESTAMPTZ,
    last_login_ip  VARCHAR(45),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 모든 관리자 행위 기록 (조회 포함)
CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT      NOT NULL REFERENCES admins(id),
    action      VARCHAR(40) NOT NULL,
    target_type VARCHAR(20),
    target_id   BIGINT,
    reason      VARCHAR(500),
    before_data JSONB,
    after_data  JSONB,
    ip          VARCHAR(45),
    user_agent  VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_audit_admin ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_logs(target_type, target_id, created_at DESC);

CREATE TABLE user_sanctions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    admin_id    BIGINT      NOT NULL REFERENCES admins(id),
    type        VARCHAR(12) NOT NULL,   -- WARN|WRITE_BAN|SUSPEND|TERMINATE
    reason      VARCHAR(500) NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at     TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_sanctions_active ON user_sanctions(user_id, ends_at)
    WHERE released_at IS NULL;

-- 신고 처리 큐 (SLA 48h)
CREATE TABLE moderation_queue (
    id                BIGSERIAL PRIMARY KEY,
    source_type       VARCHAR(20) NOT NULL,   -- REVIEW|POST|CLUB_POST|CLUB|USER
    source_id         BIGINT      NOT NULL,
    reason            VARCHAR(30) NOT NULL,
    report_count      INT         NOT NULL DEFAULT 1,
    priority          SMALLINT    NOT NULL DEFAULT 3,   -- 1(높음)~5
    sla_due_at        TIMESTAMPTZ NOT NULL,
    status            VARCHAR(12) NOT NULL DEFAULT 'PENDING', -- PENDING|IN_REVIEW|RESOLVED
    assigned_admin_id BIGINT      REFERENCES admins(id),
    resolution        VARCHAR(20),  -- KEEP|HIDE|DELETE|SANCTION
    resolution_note   VARCHAR(500),
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id)
);
CREATE INDEX idx_moderation_queue_open ON moderation_queue(status, sla_due_at);

-- 운영 스위치 (긴급 푸시 중단 등)
CREATE TABLE ops_flags (
    key         VARCHAR(50) PRIMARY KEY,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    note        VARCHAR(300),
    updated_by  BIGINT      REFERENCES admins(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO ops_flags(key, enabled, note) VALUES
    ('PUSH_ENABLED',        TRUE, '전체 푸시 발송 스위치 (긴급 킬스위치)'),
    ('CLUB_CREATION_OPEN',  TRUE, '모임 생성 허용'),
    ('SIGNUP_OPEN',         TRUE, '신규 가입 허용');
