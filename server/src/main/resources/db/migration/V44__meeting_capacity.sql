-- 모임 최대 인원(선택) — 비우면 제한 없이 받는다. 정원이 차면 참여를 막는다.
ALTER TABLE club_meetings ADD COLUMN max_attendees INT CHECK (max_attendees > 0);
