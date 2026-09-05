package app.bookey.api.auth.verifier;

import app.bookey.api.auth.SocialProfile;
import app.bookey.api.auth.SocialTokenVerifier;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.user.AuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 구글 id_token 검증 (tokeninfo 엔드포인트). */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier implements SocialTokenVerifier {

    private static final String TOKEN_INFO = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public SocialProfile verify(String idToken) {
        try {
            Map<?, ?> body = bookApiRestClient.get()
                    .uri(TOKEN_INFO + idToken)
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.get("sub") == null) {
                throw ApiException.of(ErrorCode.INVALID_TOKEN);
            }
            validateAudience((String) body.get("aud"));
            return new SocialProfile(
                    AuthProvider.GOOGLE,
                    (String) body.get("sub"),
                    (String) body.get("email"),
                    (String) body.get("name"),
                    (String) body.get("picture"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed", e);
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }

    private void validateAudience(String audience) {
        if (audience == null || audience.isBlank()) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
        var allowed = properties.oauth().googleClientIds();
        if (allowed == null || allowed.isEmpty() || allowed.stream().allMatch(String::isBlank)) {
            log.warn("Google OAuth audience validation is not configured");
            return;
        }
        boolean matched = allowed.stream()
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .anyMatch(id -> id.equals(audience));
        if (!matched) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }
}
