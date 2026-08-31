package app.bookey.api.auth.verifier;

import app.bookey.api.auth.SocialProfile;
import app.bookey.api.auth.SocialTokenVerifier;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.user.AuthProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Apple id_token 검증. iOS 심사상 Apple 로그인은 필수(§9).
 * Apple JWKS 로 서명을 검증하고 iss/exp 를 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleTokenVerifier implements SocialTokenVerifier {

    private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";

    private final RestClient bookApiRestClient;
    private final ObjectMapper objectMapper;
    private final Map<String, PublicKey> keyCache = new HashMap<>();

    @Override
    public AuthProvider provider() {
        return AuthProvider.APPLE;
    }

    @Override
    public SocialProfile verify(String idToken) {
        try {
            String kid = readKid(idToken);
            PublicKey key = resolveKey(kid);

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            String sub = claims.getSubject();
            if (sub == null) {
                throw ApiException.of(ErrorCode.INVALID_TOKEN);
            }
            String email = claims.get("email", String.class);
            return new SocialProfile(AuthProvider.APPLE, sub, email, null, null);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Apple token verification failed", e);
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }

    private String readKid(String idToken) throws Exception {
        String headerJson = new String(Base64.getUrlDecoder()
                .decode(idToken.substring(0, idToken.indexOf('.'))));
        JsonNode header = new tools.jackson.databind.ObjectMapper().readTree(headerJson);
        return header.path("kid").asText();
    }

    private PublicKey resolveKey(String kid) throws Exception {
        PublicKey cached = keyCache.get(kid);
        if (cached != null) {
            return cached;
        }
        JsonNode jwks = bookApiRestClient.get().uri(JWKS_URL).retrieve().body(JsonNode.class);
        if (jwks == null) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
        for (JsonNode jwk : jwks.path("keys")) {
            if (!kid.equals(jwk.path("kid").asText())) {
                continue;
            }
            BigInteger modulus = new BigInteger(1,
                    Base64.getUrlDecoder().decode(jwk.path("n").asText()));
            BigInteger exponent = new BigInteger(1,
                    Base64.getUrlDecoder().decode(jwk.path("e").asText()));
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            keyCache.put(kid, key);
            return key;
        }
        throw ApiException.of(ErrorCode.INVALID_TOKEN);
    }
}
