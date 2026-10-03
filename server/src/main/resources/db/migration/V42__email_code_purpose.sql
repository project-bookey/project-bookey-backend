-- 비밀번호 재설정 — 이메일 인증 코드에 용도를 붙여 가입 코드와 재설정 코드를 따로 판정한다.
-- 기존 행은 모두 가입 코드다.
ALTER TABLE email_verifications ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'SIGNUP';

DROP INDEX idx_email_verifications_email;
CREATE INDEX idx_email_verifications_email_purpose ON email_verifications(email, purpose, id DESC);
