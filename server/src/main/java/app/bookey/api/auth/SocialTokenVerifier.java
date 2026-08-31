package app.bookey.api.auth;

import app.bookey.domain.user.AuthProvider;

public interface SocialTokenVerifier {

    AuthProvider provider();

    /** 토큰을 검증하고 사용자 식별 정보를 돌려준다. 실패 시 ApiException(INVALID_TOKEN). */
    SocialProfile verify(String token);
}
