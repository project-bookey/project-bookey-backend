-- 오려둔 문장(밑줄) + 광장 피드 (계획: docs/superpowers/plans/2026-09-01-quotes-plaza.md)
CREATE TABLE book_quotes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id           BIGINT NOT NULL REFERENCES books(id),
    reading_record_id BIGINT REFERENCES reading_records(id) ON DELETE SET NULL,
    content           VARCHAR(500) NOT NULL,
    page              INT CHECK (page >= 1),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_book_quotes_user    ON book_quotes(user_id, created_at DESC);
CREATE INDEX idx_book_quotes_book    ON book_quotes(book_id, created_at DESC);
CREATE INDEX idx_book_quotes_created ON book_quotes(created_at DESC, id DESC);

CREATE TABLE quote_agrees (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quote_id   BIGINT NOT NULL REFERENCES book_quotes(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, quote_id)
);
CREATE INDEX idx_quote_agrees_quote ON quote_agrees(quote_id);

-- B2(광장 완독 자랑 피드)용이지만 마이그레이션은 한 번에 포함한다.
CREATE INDEX idx_reading_records_finished_feed
    ON reading_records(finished_at DESC) WHERE status = 'FINISHED';
