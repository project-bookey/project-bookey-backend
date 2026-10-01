-- 함께 독서 기록 카드 꾸미기(한마디·스티커·사진)를 걷어낸다 — 소감은 독후감으로, 카드는 노트 스티커로 쓴다.
DROP INDEX IF EXISTS idx_club_activity_cards_club;
ALTER TABLE club_activity_cards
    DROP COLUMN caption,
    DROP COLUMN decorations_json,
    DROP COLUMN photo_url,
    DROP COLUMN photo_key;
CREATE INDEX idx_club_activity_cards_user ON club_activity_cards(user_id, id DESC);
