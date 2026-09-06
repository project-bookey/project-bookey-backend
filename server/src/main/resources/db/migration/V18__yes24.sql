-- ============================================================
--  bookey V18 — YES24 오픈API 연동 (§14.2 애드온 링크 · §F1 메타 보강)
-- ============================================================

-- YES24 상품 링크와 제휴 애드온 링크(이 링크로 구매하면 책갈피 적립의 근거가 된다), 목차
ALTER TABLE books ADD COLUMN purchase_link     TEXT;
ALTER TABLE books ADD COLUMN addon_link        TEXT;
ALTER TABLE books ADD COLUMN table_of_contents TEXT;
