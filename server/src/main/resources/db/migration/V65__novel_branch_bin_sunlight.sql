-- 릴레이노벨 가지 상한·나뭇가지 수거함·햇살(소설 전용 재화).
-- 북키 지갑(wallets·책갈피)과 섞지 않고 소설 전용 테이블을 둔다.

-- 배달은 늘 10명에게 간다. 대신 회차 하나에 붙을 수 있는 가지 수(10~20)를 작품마다 정한다.
ALTER TABLE novels ADD COLUMN branch_limit INTEGER NOT NULL DEFAULT 10 CHECK (branch_limit BETWEEN 10 AND 20);
UPDATE novels SET delivery_count = 10 WHERE mode = 'OPEN';

-- 회차 상태: 나무에 있음(ACTIVE) → 수거함(PRUNED, 일주일 안에 살리거나 버림) → 지워짐(REMOVED).
-- judged_from 부터 한 달 동안 조회가 50회를 못 넘기면 수거한다. 살리면 그날부터 다시 센다.
ALTER TABLE novel_chapters ADD COLUMN state VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
    CHECK (state IN ('ACTIVE', 'PRUNED', 'REMOVED'));
ALTER TABLE novel_chapters ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE novel_chapters ADD COLUMN judged_from TIMESTAMPTZ;
UPDATE novel_chapters SET judged_from = created_at;
ALTER TABLE novel_chapters ALTER COLUMN judged_from SET NOT NULL;
ALTER TABLE novel_chapters ALTER COLUMN judged_from SET DEFAULT now();
ALTER TABLE novel_chapters ADD COLUMN pruned_at TIMESTAMPTZ;
ALTER TABLE novel_chapters ADD COLUMN purge_at TIMESTAMPTZ;
CREATE INDEX idx_novel_chapters_judge ON novel_chapters(judged_from) WHERE state = 'ACTIVE';
CREATE INDEX idx_novel_chapters_purge ON novel_chapters(purge_at) WHERE state = 'PRUNED';
CREATE INDEX idx_novel_chapters_bin ON novel_chapters(author_id, purge_at) WHERE state = 'PRUNED';

-- 조회수는 한 사람당 한 번만 센다(글쓴이 본인은 세지 않는다).
CREATE TABLE novel_chapter_views (
    chapter_id BIGINT      NOT NULL REFERENCES novel_chapters(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chapter_id, user_id)
);

-- 내 글 잇기 — 햇살을 내고 내 회차를 나에게 배달한다. 그만두거나 시간이 지나면 낸 햇살을 돌려준다.
ALTER TABLE novel_deliveries ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'RANDOM' CHECK (source IN ('RANDOM', 'SELF'));
ALTER TABLE novel_deliveries ADD COLUMN sunlight_paid INTEGER NOT NULL DEFAULT 0;
-- 무작위 배달만 같은 회차를 한 사람에게 두 번 보내지 않는다. 내 글 잇기는 자리가 남는 한 여러 번 할 수 있다.
ALTER TABLE novel_deliveries DROP CONSTRAINT IF EXISTS novel_deliveries_chapter_id_recipient_id_key;
CREATE UNIQUE INDEX uq_novel_deliveries_random_once ON novel_deliveries(chapter_id, recipient_id) WHERE source = 'RANDOM';
CREATE INDEX idx_novel_deliveries_chapter ON novel_deliveries(chapter_id) WHERE status = 'DELIVERED';

-- 햇살 — 수거함의 가지를 살리거나 내 글에 이어 쓸 때 쓴다. 잔액은 원장(transactions)의 캐시다.
CREATE TABLE novel_sunlight_wallets (
    user_id    BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    balance    INTEGER     NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE TABLE novel_sunlight_transactions (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(20) NOT NULL,
    amount     INTEGER     NOT NULL,
    ref_type   VARCHAR(30),
    ref_id     BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_novel_sunlight_transactions_user ON novel_sunlight_transactions(user_id, id DESC);
CREATE TABLE novel_sunlight_purchases (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider       VARCHAR(10)  NOT NULL,
    product_id     VARCHAR(100) NOT NULL,
    order_id       VARCHAR(120) NOT NULL UNIQUE,
    transaction_id VARCHAR(200) UNIQUE,
    quantity       INTEGER      NOT NULL,
    amount_krw     INTEGER      NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_novel_sunlight_purchases_user ON novel_sunlight_purchases(user_id, id DESC);
