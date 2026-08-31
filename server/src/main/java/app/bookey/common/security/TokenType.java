package app.bookey.common.security;

public enum TokenType {
    /** 서비스 사용자 액세스 토큰 (/api/v1/**) */
    USER_ACCESS,
    /** 서비스 사용자 리프레시 토큰 */
    USER_REFRESH,
    /** 관리자 액세스 토큰 (/admin/v1/**) — 서비스 API 접근 불가 */
    ADMIN_ACCESS
}
