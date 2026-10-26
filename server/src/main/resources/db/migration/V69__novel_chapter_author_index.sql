-- 마이의 '내가 이어 쓴 이야기': 내가 쓴 나무에 있는 회차를 작품별로 모은다.
CREATE INDEX idx_novel_chapters_author ON novel_chapters(author_id, novel_id, created_at)
    WHERE state = 'ACTIVE';
