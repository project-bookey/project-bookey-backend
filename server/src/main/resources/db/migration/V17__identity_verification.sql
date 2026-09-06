-- ============================================================
--  bookey V17 — 휴대폰 본인인증 (가입 본인인증 강화 2단계)
--  포트원(PortOne) 본인인증 연동 — CI 유니크로 1인 다계정 가입을 막는다.
-- ============================================================

ALTER TABLE users ADD COLUMN real_name            VARCHAR(50);
ALTER TABLE users ADD COLUMN phone                VARCHAR(20);
ALTER TABLE users ADD COLUMN birth_date           DATE;
ALTER TABLE users ADD COLUMN ci                   VARCHAR(128);   -- 연계정보 — 사람당 1개
ALTER TABLE users ADD COLUMN di                   VARCHAR(128);   -- 중복가입확인정보
ALTER TABLE users ADD COLUMN identity_verified_at TIMESTAMPTZ;

-- 같은 사람(CI)의 중복 가입 차단. NULL(미인증·이메일 인증 가입자)은 여럿 허용.
CREATE UNIQUE INDEX uq_users_ci ON users(ci) WHERE ci IS NOT NULL;
