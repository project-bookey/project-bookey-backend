-- 홈 열매 밭 새로고침 — 무료를 다 쓰면 광고를 보고 하루 몇 번 더, 그것도 다 쓰면 결제로 한 번씩 산다.
-- 광고 횟수는 무료 횟수와 같은 날짜(free_reset_date)로 센다 — 날이 바뀌면 둘 다 0 으로 본다.
-- 결제한 횟수는 날이 바뀌어도 남고, 새로고침할 때 하나씩 쓴다.
ALTER TABLE novel_home_feeds ADD COLUMN ad_used_today  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE novel_home_feeds ADD COLUMN paid_refreshes INT      NOT NULL DEFAULT 0;

-- 새로고침 결제 주문 — 릴레이노벨 앱의 소모성 상품(한 번 사면 새로고침 한 번). 북키 책갈피 주문(bookmark_purchases)과 따로 둔다.
CREATE TABLE novel_refresh_purchases (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider       VARCHAR(10)  NOT NULL,
    product_id     VARCHAR(100) NOT NULL,
    order_id       VARCHAR(120) NOT NULL UNIQUE,
    amount_krw     INT          NOT NULL,
    transaction_id TEXT,                     -- 확인한 스토어 결제 — Apple transactionId, Google 구매 토큰(길이가 정해져 있지 않다)
    paid_at        TIMESTAMPTZ,              -- 결제를 확인한 때. 비어 있으면 아직 확인 전
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    -- 스토어 결제 하나로 주문 둘을 확인하지 못하게 한다. 확인 전 주문(NULL)끼리는 겹쳐도 된다.
    CONSTRAINT uq_novel_refresh_purchases_transaction UNIQUE (provider, transaction_id)
);
CREATE INDEX idx_novel_refresh_purchases_user ON novel_refresh_purchases(user_id);
