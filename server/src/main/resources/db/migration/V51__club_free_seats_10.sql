-- 무료 정원이 3명에서 10명으로 늘었다 — 아직 열려 있는 클럽 가운데 10명보다 작은 클럽은 10명으로 올린다.
-- 책갈피로 4~6명까지 늘려 둔 클럽도 함께 올린다(쓴 책갈피는 돌려주지 않는다). 끝난 클럽은 그대로 둔다.
-- 이후 자리는 10명 단위로만 늘린다(bookey.club.seat-step).
UPDATE clubs
   SET member_limit = 10
 WHERE member_limit < 10
   AND status IN ('RECRUITING', 'ACTIVE');
