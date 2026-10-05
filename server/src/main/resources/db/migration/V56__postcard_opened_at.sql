-- 엽서 열람 — 받은 사람이 엽서를 처음 연 시각. 비어 있으면 아직 열지 않은 엽서(목록에서 닫힌 봉투)다.
-- 예전 앱은 목록에 본문을 다 펼쳐 보였으니, 이미 받아 둔 엽서는 받은 때 연 것으로 본다(2026-10-05, 사용자 결정).
ALTER TABLE postcards ADD COLUMN opened_at TIMESTAMPTZ;
UPDATE postcards SET opened_at = created_at;
