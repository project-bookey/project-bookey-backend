-- 홈 콘텐츠: 이벤트 배너, 에디터 픽 (스펙: app 저장소 2026-08-31-home-redesign-design.md)
CREATE TABLE banners (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    subtitle VARCHAR(200),
    image_url VARCHAR(500),
    bg_color VARCHAR(20),   -- 이미지 없을 때 단색 배경 (#RRGGBB)
    link_url VARCHAR(500),  -- http(s)=외부, 그 외=앱 내 라우트
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE editor_picks (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id),
    sort_order INT NOT NULL DEFAULT 0,
    note VARCHAR(200),      -- 운영 메모 (노출 안 함)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_editor_picks_book ON editor_picks(book_id);

-- 인기 집계(book_id 기준 GROUP BY)용
CREATE INDEX IF NOT EXISTS ix_reading_records_book ON reading_records(book_id);
