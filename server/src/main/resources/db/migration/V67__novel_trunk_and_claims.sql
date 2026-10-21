-- 릴레이노벨 이어 쓰기 개편(2026-10-21 사용자 결정).
-- 무작위 배달을 없애고, 홈에는 아직 자리가 남은 회차를 열매로 놓는다. 열매를 골라 쓰기 시작하면 그 사람이 자리를 맡는다.
-- 첫 작가는 자기 회차로만 이어지는 줄기를 계속 쓴다. 줄기는 가지 자리에 들지 않고 수거되지 않는다.

-- 회차 하나에 붙는 가지는 2~5개(기본 3). 예전 작품(10~20)은 새 범위 안으로 맞춘다.
ALTER TABLE novels DROP CONSTRAINT IF EXISTS novels_branch_limit_check;
UPDATE novels SET branch_limit = LEAST(GREATEST(branch_limit, 2), 5);
ALTER TABLE novels ALTER COLUMN branch_limit SET DEFAULT 3;
ALTER TABLE novels ADD CONSTRAINT novels_branch_limit_check CHECK (branch_limit BETWEEN 2 AND 5);

-- 첫 작가의 줄기. 열매로 퍼뜨리는 작품의 뿌리 회차부터 줄기로 둔다.
ALTER TABLE novel_chapters ADD COLUMN trunk BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE novel_chapters c SET trunk = TRUE
  FROM novels n
 WHERE n.id = c.novel_id AND n.mode = 'OPEN' AND c.parent_chapter_id IS NULL;
CREATE INDEX idx_novel_chapters_trunk ON novel_chapters(novel_id, chapter_number) WHERE trunk;

-- 열매를 골라 쓰기 시작하면 맡는 자리(CLAIM). 예전에 무작위로 보낸 열매(RANDOM)는 마감까지 그대로 쓸 수 있다.
ALTER TABLE novel_deliveries DROP CONSTRAINT IF EXISTS novel_deliveries_source_check;
ALTER TABLE novel_deliveries ADD CONSTRAINT novel_deliveries_source_check
    CHECK (source IN ('RANDOM', 'SELF', 'CLAIM'));

-- 홈 열매 묶음은 이제 회차 id 로 둔다. 예전 작품 묶음(novel_ids)은 읽지 않는다.
ALTER TABLE novel_home_feeds ADD COLUMN chapter_ids BIGINT[] NOT NULL DEFAULT '{}';

-- 새로고침하면 아직 보여 주지 않은 회차를 놓는다. 일주일이 지난 기록은 다시 보여 줄 수 있다.
CREATE TABLE novel_home_seen (
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id BIGINT      NOT NULL REFERENCES novel_chapters(id) ON DELETE CASCADE,
    shown_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, chapter_id)
);
