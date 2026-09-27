CREATE TABLE expo_push_tickets (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       VARCHAR(100) NOT NULL UNIQUE,
    device_id       BIGINT NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count   SMALLINT NOT NULL DEFAULT 0,
    next_check_at   TIMESTAMPTZ NOT NULL,
    error_code      VARCHAR(80),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_expo_push_tickets_pending
    ON expo_push_tickets(next_check_at) WHERE status = 'PENDING';
