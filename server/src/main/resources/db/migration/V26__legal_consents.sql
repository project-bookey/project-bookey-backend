ALTER TABLE users
    ADD COLUMN terms_version VARCHAR(20),
    ADD COLUMN terms_agreed_at TIMESTAMPTZ,
    ADD COLUMN privacy_version VARCHAR(20),
    ADD COLUMN privacy_agreed_at TIMESTAMPTZ;

COMMENT ON COLUMN users.terms_version IS '가입 시 동의한 이용약관 버전';
COMMENT ON COLUMN users.privacy_version IS '가입 시 동의한 개인정보 수집·이용 문서 버전';
