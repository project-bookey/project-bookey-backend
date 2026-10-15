-- 홈 열매 밭 새로고침 — 북키 지갑(wallets)을 재사용하지 않고 따로 둔다.
-- 사용자마다 피드 자리에 놓인 작품 묶음(이 순서대로 보여 준다)과 오늘 쓴 무료 새로고침 횟수를 둔다.
CREATE TABLE novel_home_feeds (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    novel_ids       BIGINT[]    NOT NULL DEFAULT '{}',
    free_used_today SMALLINT    NOT NULL DEFAULT 0,
    free_reset_date DATE        NOT NULL,    -- 횟수를 센 KST 날짜 — 날이 바뀌면 0 으로 본다
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);
