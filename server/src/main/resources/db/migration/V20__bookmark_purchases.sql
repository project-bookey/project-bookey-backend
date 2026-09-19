-- ============================================================
--  bookey V20 — 책갈피 단건 결제 주문
-- ============================================================

CREATE TABLE bookmark_purchases (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(10)  NOT NULL,
    product_id      VARCHAR(100) NOT NULL,
    order_id        VARCHAR(120) NOT NULL UNIQUE,
    payment_key     VARCHAR(200) UNIQUE,
    quantity        INT          NOT NULL,
    bonus_quantity  INT          NOT NULL DEFAULT 0,
    amount_krw      INT          NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_bookmark_purchases_user ON bookmark_purchases(user_id, id DESC);
