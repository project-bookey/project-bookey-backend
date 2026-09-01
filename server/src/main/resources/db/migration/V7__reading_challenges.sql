-- 챌린지 — 독서시간 예산 타임워치 (스펙: app 저장소 2026-09-01-reading-challenge-design.md)
CREATE TABLE reading_challenges (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reading_record_id BIGINT NOT NULL REFERENCES reading_records(id) ON DELETE CASCADE,
    budget_sec        INT NOT NULL CHECK (budget_sec > 0),
    elapsed_sec       INT NOT NULL DEFAULT 0,
    running           BOOLEAN NOT NULL DEFAULT FALSE,
    last_started_at   TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|SUCCEEDED|FAILED|CANCELLED
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_challenges_active ON reading_challenges(reading_record_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_challenges_user_status ON reading_challenges(user_id, status);
