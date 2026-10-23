-- 열매 탭 순위(이번 주 많이 이어 쓴 이야기): 지난 7일 동안 나무에 붙은 이어 쓰기 회차를 작품별로 센다.
CREATE INDEX idx_novel_chapters_recent ON novel_chapters(created_at)
    WHERE state = 'ACTIVE' AND parent_chapter_id IS NOT NULL;
