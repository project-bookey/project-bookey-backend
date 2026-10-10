-- 앱 버전 안내 — 플랫폼별 최소 지원 버전(이보다 낮으면 강제 업데이트)과 최신 버전(낮으면 업데이트 권유).
-- 앱은 GET /api/v1/public/app-config 로 읽는다. 행이 늘 둘(IOS·ANDROID)이라 관리자는 고치기만 한다.
CREATE TABLE app_release_configs (
    platform              VARCHAR(10)  PRIMARY KEY,
    min_supported_version VARCHAR(20)  NOT NULL DEFAULT '0.0.0',
    latest_version        VARCHAR(20)  NOT NULL DEFAULT '0.0.0',
    store_url             VARCHAR(500),
    update_message        VARCHAR(300),
    updated_by            BIGINT       REFERENCES admins (id) ON DELETE SET NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO app_release_configs (platform) VALUES ('IOS'), ('ANDROID');

-- 점검 안내 — 기간 동안 앱이 점검 화면을 띄울 수 있게 알려 준다. 서버가 요청을 막지는 않는다.
CREATE TABLE maintenance_windows (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(100) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ  NOT NULL,
    cancelled_at TIMESTAMPTZ,
    created_by   BIGINT       NOT NULL REFERENCES admins (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT ck_maintenance_window_period CHECK (ends_at > starts_at)
);
CREATE INDEX idx_maintenance_windows_open ON maintenance_windows (ends_at) WHERE cancelled_at IS NULL;
