-- 독후감 노트 모드 · 모임 독후감. 모임 노트북(V31)은 독후감 노트 모드로 합쳐 폐기한다.
-- format: TEXT(마크다운 본문) | NOTE(캔버스 문서). document 는 NOTE 만 쓰는 앱 소유 JSON — 서버는 pages 수·크기만 검사한다.
-- club_id: 모임 안에서 만든 독후감. 이때 visibility 는 PUBLIC(모임+광장) | CLUB(모임만).
-- visibility 는 VARCHAR(10) 이고 CHECK 제약이 없어 'CLUB' 을 그대로 담을 수 있다.
ALTER TABLE posts
    ADD COLUMN format   VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN document JSONB,
    ADD COLUMN club_id  BIGINT REFERENCES clubs(id) ON DELETE CASCADE;

CREATE INDEX idx_posts_club ON posts(club_id, created_at DESC) WHERE club_id IS NOT NULL;

-- 모임 노트북 폐기 — 데이터는 로컬 시드뿐이다. 사진 테이블이 페이지를 참조하므로 먼저 지운다.
DROP TABLE IF EXISTS club_note_images;
DROP TABLE IF EXISTS club_note_pages;
