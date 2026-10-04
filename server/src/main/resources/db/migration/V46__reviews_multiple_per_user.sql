-- 리뷰는 한 사람이 한 책(읽기 기록)에 여러 개 쓸 수 있다 — 기록당 하나로 묶던 제약을 푼다.
-- 책 평점은 사람마다 가장 최근 리뷰 하나만 센다(ReviewRepository.verifiedRating/overallRating).
ALTER TABLE reviews DROP CONSTRAINT reviews_user_id_reading_record_id_key;

-- 제약이 갖고 있던 user_id 선두 인덱스를 대신한다 — 내 리뷰 목록, 평점의 '사람별 최신' 판정.
CREATE INDEX idx_reviews_user_book ON reviews(user_id, book_id);
