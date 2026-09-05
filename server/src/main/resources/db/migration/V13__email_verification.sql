-- ============================================================
--  bookey V13 — 가입 이메일 인증 코드
--  가입 본인인증 강화: 코드 검증을 통과한 이메일만 가입할 수 있다.
-- ============================================================

CREATE TABLE email_verifications (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,           -- 평문 코드는 저장하지 않는다 (SHA-256)
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempt_count SMALLINT     NOT NULL DEFAULT 0, -- 검증 실패 누적 — 상한 초과 시 코드 무효
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_email_verifications_email ON email_verifications(email, id DESC);

-- 가입 시 이메일 인증을 통과한 시각
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
