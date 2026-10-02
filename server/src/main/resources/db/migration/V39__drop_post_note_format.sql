-- 독후감 노트 모드 폐기 — 독후감은 마크다운 본문 하나로만 쓴다(V32 에서 넣은 format·document 를 지운다).
-- 예전 노트 독후감은 앱이 올릴 때 함께 보낸 노트 속 글(body_md)과 사진(post_images)이 남아 일반 독후감으로 보인다.
-- 캔버스 문서(document)는 버린다. club_id 는 모임 독후감이 계속 쓰므로 그대로 둔다.
ALTER TABLE posts
    DROP COLUMN document,
    DROP COLUMN format;
