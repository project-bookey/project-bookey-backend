-- 한 마디 — 책을 다 읽었을 때(완독)나 내려놓을 때(하차) 남기는 한 줄. 도서 상세에 최신순으로 돌아가며 보인다.
-- 읽기 기록(회차)마다 하나 — 다시 쓰면 고쳐지고, 재독은 새 기록이라 새로 남길 수 있다.
CREATE TABLE book_remarks (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id           BIGINT       NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    reading_record_id BIGINT       NOT NULL UNIQUE REFERENCES reading_records(id) ON DELETE CASCADE,
    kind              VARCHAR(20)  NOT NULL,              -- FINISHED|ABANDONED — 남길 때 기록의 상태
    body              VARCHAR(60)  NOT NULL,
    written_at        TIMESTAMPTZ  NOT NULL,              -- 마지막으로 쓴 때 — 고치면 다시 맨 앞으로 온다
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_book_remarks_book ON book_remarks(book_id, written_at DESC, id DESC);
