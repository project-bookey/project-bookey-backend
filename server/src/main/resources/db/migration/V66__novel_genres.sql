-- 릴레이노벨 작품 장르를 최대 3개까지 고른다. genre 는 첫 번째 장르로 남겨 예전 앱과 목록 표시에 쓴다.
ALTER TABLE novels ADD COLUMN genres TEXT[] NOT NULL DEFAULT '{}';
UPDATE novels SET genres = ARRAY[genre];
ALTER TABLE novels ADD CONSTRAINT novels_genres_count CHECK (cardinality(genres) BETWEEN 1 AND 3);
ALTER TABLE novels ALTER COLUMN genres DROP DEFAULT;
