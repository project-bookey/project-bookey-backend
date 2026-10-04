ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;

CREATE TABLE deleted_email_hashes (
    email_hash VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 이미 종료된 계정도 배치 정리 대상에 포함한다. 기존 행은 이메일이 익명화되어 차단 목록 복구가 불가능하다.
UPDATE users
SET deletion_requested_at = COALESCE(updated_at, now())
WHERE status = 'TERMINATED' AND deletion_requested_at IS NULL;

-- 기존 중복 닉네임은 가장 먼저 가입한 사용자만 유지하고 나머지는 식별 가능한 접미사를 붙인다.
WITH duplicates AS (
    SELECT id,
           row_number() OVER (PARTITION BY lower(btrim(nickname)) ORDER BY id) AS occurrence
    FROM users
    WHERE status <> 'TERMINATED'
)
UPDATE users u
SET nickname = left(btrim(u.nickname), 40) || '_' || u.id
FROM duplicates d
WHERE u.id = d.id AND d.occurrence > 1;

CREATE UNIQUE INDEX uq_users_active_nickname_ci
    ON users (lower(btrim(nickname)))
    WHERE status <> 'TERMINATED';

-- 공유 모임 기록은 작성자가 삭제되어도 보존하되 개인정보 연결만 제거한다.
ALTER TABLE club_meetings DROP CONSTRAINT club_meetings_created_by_fkey;
ALTER TABLE club_meetings ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE club_meetings
    ADD CONSTRAINT club_meetings_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE club_chat_unlocks DROP CONSTRAINT club_chat_unlocks_paid_by_user_id_fkey;
ALTER TABLE club_chat_unlocks ALTER COLUMN paid_by_user_id DROP NOT NULL;
ALTER TABLE club_chat_unlocks
    ADD CONSTRAINT club_chat_unlocks_paid_by_user_id_fkey
    FOREIGN KEY (paid_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_users_deletion_due
    ON users (deletion_requested_at)
    WHERE status = 'TERMINATED';
