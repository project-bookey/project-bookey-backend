package app.bookey.domain.user;

/** 이메일 인증 코드의 용도 — 가입 코드와 비밀번호 재설정 코드는 서로 대신 쓸 수 없다. */
public enum EmailCodePurpose {
    SIGNUP,
    PASSWORD_RESET
}
