package app.bookey.api.auth.verifier;

import app.bookey.api.auth.SocialProfile;
import app.bookey.api.auth.SocialTokenVerifier;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.user.AuthProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 로그인. token 을 그대로 식별자로 사용한다.
 * prod 프로필에서는 빈 자체가 등록되지 않는다.
 */
@Component
@Profile("!prod")
public class DevTokenVerifier implements SocialTokenVerifier {

    @Override
    public AuthProvider provider() {
        return AuthProvider.DEV;
    }

    @Override
    public SocialProfile verify(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
        String uid = token.trim();
        return new SocialProfile(AuthProvider.DEV, uid, uid + "@dev.local", "테스터-" + uid, null);
    }
}
