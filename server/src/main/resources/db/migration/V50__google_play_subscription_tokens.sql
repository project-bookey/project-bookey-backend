ALTER TABLE subscriptions ALTER COLUMN original_transaction_id TYPE TEXT;

-- 과거 동시 요청으로 같은 스토어 토큰이 중복 저장됐다면 가장 최신 행만 유지한다.
DELETE FROM subscriptions older
USING subscriptions newer
WHERE older.store = newer.store
  AND older.original_transaction_id = newer.original_transaction_id
  AND older.original_transaction_id IS NOT NULL
  AND older.id < newer.id;

CREATE UNIQUE INDEX uq_subscriptions_store_transaction
    ON subscriptions (store, original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;
