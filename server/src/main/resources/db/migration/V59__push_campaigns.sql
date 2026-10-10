-- 관리자 전체 푸시(캠페인). NOTICE(서비스 공지 — 가입자 전체)와 MARKETING(광고 — 수신 동의자만)을 나눈다.
-- 잡이 대상자를 회원 id 순으로 조금씩 펼쳐(cursor_user_id) 알림 행을 만들고, 방해 금지·야간(광고 21–08시)을 피해 보낸다.
CREATE TABLE push_campaigns (
    id             BIGSERIAL    PRIMARY KEY,
    kind           VARCHAR(12)  NOT NULL,                       -- NOTICE | MARKETING
    title          VARCHAR(100) NOT NULL,
    body           VARCHAR(300) NOT NULL,
    link_url       VARCHAR(500),
    status         VARCHAR(12)  NOT NULL DEFAULT 'SCHEDULED',   -- SCHEDULED | SENDING | DONE | CANCELLED
    scheduled_at   TIMESTAMPTZ  NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    cursor_user_id BIGINT       NOT NULL DEFAULT 0,
    audience_done  BOOLEAN      NOT NULL DEFAULT FALSE,
    target_count   INT          NOT NULL DEFAULT 0,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_by     BIGINT       NOT NULL REFERENCES admins (id),
    cancelled_by   BIGINT       REFERENCES admins (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_push_campaigns_due ON push_campaigns (scheduled_at) WHERE status IN ('SCHEDULED', 'SENDING');

-- 캠페인이 만든 알림 — 개인 알림 디스패처·일일 한도·전환율 집계에서 뺀다.
ALTER TABLE notifications ADD COLUMN campaign_id BIGINT REFERENCES push_campaigns (id) ON DELETE SET NULL;
CREATE INDEX idx_notifications_campaign_due ON notifications (scheduled_at) WHERE campaign_id IS NOT NULL AND sent_at IS NULL;
CREATE INDEX idx_notifications_campaign ON notifications (campaign_id) WHERE campaign_id IS NOT NULL;

-- 광고성 정보 수신 동의자 고르기 — 회원별 MARKETING 최신 결정
CREATE INDEX IF NOT EXISTS idx_user_consents_kind_user ON user_consents (kind, user_id, id DESC);
