-- 클럽 머리 배경 사진 — 호스트가 올린다. 바꾸거나 빼면 이전 파일은 키로 찾아 지운다.
ALTER TABLE clubs ADD COLUMN background_url TEXT;
ALTER TABLE clubs ADD COLUMN background_key VARCHAR(255);
