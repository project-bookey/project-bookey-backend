package app.bookey.domain.user;

public enum AuthProvider {
    APPLE,
    GOOGLE,
    KAKAO,
    /** 로컬 개발용 — prod 프로필에서는 거부된다. */
    DEV
}
