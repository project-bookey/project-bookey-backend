package app.bookey.api.auth.verifier;

import app.bookey.api.auth.SocialProfile;
import app.bookey.api.auth.SocialTokenVerifier;
import app.bookey.common.config.BookeyProperties;
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
    private static final String TOKEN_INFO = "https://kapi.kakao.com/v1/user/access_token_info";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SocialProfile verify(String accessToken) {
        try {
            validateApp(accessToken);
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

    private void validateApp(String accessToken) {
        Long expectedAppId = properties.oauth().kakaoAppId();
        if (expectedAppId == null) {
            log.warn("Kakao OAuth app_id validation is not configured");
            return;
        }
        Map<?, ?> body = bookApiRestClient.get()
                .uri(TOKEN_INFO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        Object appId = body == null ? null : body.get("app_id");
        if (!String.valueOf(expectedAppId).equals(String.valueOf(appId))) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }
}
