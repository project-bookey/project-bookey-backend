package app.bookey.api.auth.verifier;

import app.bookey.api.auth.SocialProfile;
import app.bookey.api.auth.SocialTokenVerifier;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.user.AuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 카카오 액세스 토큰 → 사용자 정보 (kapi.kakao.com/v2/user/me). */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoTokenVerifier implements SocialTokenVerifier {

    private static final String USER_ME = "https://kapi.kakao.com/v2/user/me";

    private final RestClient bookApiRestClient;

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SocialProfile verify(String accessToken) {
        try {
            Map<String, Object> body = bookApiRestClient.get()
                    .uri(USER_ME)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.get("id") == null) {
                throw ApiException.of(ErrorCode.INVALID_TOKEN);
            }
            String uid = String.valueOf(body.get("id"));

            Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
            String email = account == null ? null : (String) account.get("email");
            Map<String, Object> profile = account == null
                    ? null : (Map<String, Object>) account.get("profile");
            String nickname = profile == null ? null : (String) profile.get("nickname");
            String avatar = profile == null ? null : (String) profile.get("profile_image_url");

            return new SocialProfile(AuthProvider.KAKAO, uid, email, nickname, avatar);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Kakao token verification failed", e);
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }
}
