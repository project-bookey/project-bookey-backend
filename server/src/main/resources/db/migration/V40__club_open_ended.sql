-- 모임은 기간 없이 이어지고, 책은 한 권에 묶이지 않는다 — 만남(모임 일정)마다 책을 고르고,
-- 다가오는 만남의 책이 모임의 '지금 읽는 책'이 된다.

-- 1) 기간 없음 — 끝날 날을 비운다. 이미 끝난 모임은 언제 끝났는지 기록으로 남겨 둔다.
ALTER TABLE clubs ALTER COLUMN ends_at DROP NOT NULL;
UPDATE clubs SET ends_at = NULL WHERE status IN ('RECRUITING', 'ACTIVE');

-- 2) 지금 읽는 책 — club_books 의 한 줄을 가리킨다. 기존 모임은 만들 때 고른 책(첫 줄)으로 시작한다.
ALTER TABLE clubs ADD COLUMN current_club_book_id BIGINT REFERENCES club_books(id) ON DELETE SET NULL;
UPDATE clubs c
SET current_club_book_id = (SELECT cb.id FROM club_books cb WHERE cb.club_id = c.id ORDER BY cb.seq LIMIT 1);

-- 3) 만남마다 고르는 책(선택). 기존 만남에는 그 모임의 책을 붙여 둔다.
ALTER TABLE club_meetings ADD COLUMN book_id BIGINT REFERENCES books(id);
UPDATE club_meetings m
SET book_id = (SELECT cb.book_id FROM club_books cb WHERE cb.club_id = m.club_id ORDER BY cb.seq LIMIT 1);

-- 같은 책은 모임마다 club_books 한 줄 — 만남에서 같은 책을 다시 골라도 줄이 늘지 않는다.
CREATE UNIQUE INDEX uq_club_books_club_book ON club_books(club_id, book_id);
