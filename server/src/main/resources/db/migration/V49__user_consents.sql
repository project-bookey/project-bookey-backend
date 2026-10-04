-- 동의 이력 — 동의·철회할 때마다 한 줄씩 쌓는다. 지금 상태는 (user_id, kind) 의 마지막 행.
-- 가입 필수(TERMS·PRIVACY·AGE_14)와 선택(PROFILE_OPTIONAL·MARKETING·THIRD_PARTY_YES24)을 함께 담는다.
-- 사용자 행은 탈퇴 때 익명화만 하므로 이력은 남는다 — 동의를 받았다는 입증 자료다.
CREATE TABLE user_consents (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(30) NOT NULL,
    version    VARCHAR(20),
    agreed     BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_consents_user_kind ON user_consents (user_id, kind, id DESC);

COMMENT ON TABLE user_consents IS '동의 이력 — 동의·철회마다 한 행, 종류별 마지막 행이 현재 상태';
COMMENT ON COLUMN user_consents.kind IS 'TERMS|PRIVACY|AGE_14|PROFILE_OPTIONAL|MARKETING|THIRD_PARTY_YES24';
COMMENT ON COLUMN user_consents.version IS '동의할 때 보여 준 문서 버전(문서 없는 AGE_14 와 철회 행은 NULL)';

-- 기존 가입자의 필수 동의(V26 칼럼)를 옮긴다. 이후 users.terms_*/privacy_* 는 쓰지 않는다.
INSERT INTO user_consents (user_id, kind, version, agreed, created_at)
SELECT id, 'TERMS', terms_version, TRUE, terms_agreed_at
FROM users
WHERE terms_agreed_at IS NOT NULL;

INSERT INTO user_consents (user_id, kind, version, agreed, created_at)
SELECT id, 'PRIVACY', privacy_version, TRUE, privacy_agreed_at
FROM users
WHERE privacy_agreed_at IS NOT NULL;

COMMENT ON COLUMN users.terms_version IS '(V49 부터 미사용 — user_consents) 가입 시 동의한 이용약관 버전';
COMMENT ON COLUMN users.privacy_version IS '(V49 부터 미사용 — user_consents) 가입 시 동의한 개인정보 수집·이용 문서 버전';
