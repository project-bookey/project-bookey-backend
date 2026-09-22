-- 읽기로그 조각 — 모임 글(club_posts)에 type = 'LOG' 로 쌓는다.
-- 스포일러 가림·반응·신고를 토론 글과 같은 규칙으로 쓰기 위해 테이블을 따로 두지 않는다.
-- 조각의 사진은 한 장뿐이라 임시 업로드 없이 글과 한 요청으로 저장한다.
ALTER TABLE club_posts
    ADD COLUMN image_url          VARCHAR(500),
    ADD COLUMN image_storage_key  VARCHAR(300) UNIQUE,
    ADD COLUMN image_width        INT,
    ADD COLUMN image_height       INT,
    -- 어느 독서 세션 끝에 남긴 조각인가. 세션을 지워도 조각은 남긴다.
    ADD COLUMN reading_session_id BIGINT REFERENCES reading_sessions(id) ON DELETE SET NULL;

-- 보드(날짜별 조각)·요일 스트립 조회용.
CREATE INDEX idx_club_posts_logs ON club_posts(club_id, created_at)
    WHERE type = 'LOG' AND status = 'VISIBLE';
