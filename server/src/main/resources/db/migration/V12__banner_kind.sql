ALTER TABLE banners
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'AD';

CREATE INDEX IF NOT EXISTS ix_banners_kind_enabled_sort
    ON banners (kind, enabled, sort_order, id);
