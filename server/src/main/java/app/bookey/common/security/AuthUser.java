package app.bookey.common.security;

/** 인증된 서비스 사용자. 컨트롤러에서 @AuthenticationPrincipal 로 주입받는다. */
public record AuthUser(Long id, String handle) {
}
