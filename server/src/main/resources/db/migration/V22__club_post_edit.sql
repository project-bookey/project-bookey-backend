-- 모임 글 수정 — 작성자가 조각·토론 글의 한 줄과 쪽을 고칠 수 있게 한다.
-- 고친 흔적을 남겨야 읽는 사람이 "내가 본 그 글이 맞나"를 판단할 수 있으므로 시각을 따로 둔다.
-- NULL 이면 한 번도 고치지 않은 글이다(updated_at 은 반응·댓글 수 변화에도 움직여 구분이 안 된다).
ALTER TABLE club_posts
    ADD COLUMN edited_at TIMESTAMPTZ;
