package app.bookey.api.social.payment;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/** App Store Server API — 앱에서 받은 transactionId 를 Apple 서버로 다시 조회한다. */
@Component
@RequiredArgsConstructor
public class AppStorePaymentClient {

    private static final String PROD_API = "https://api.storekit.apple.com";
    private static final String SANDBOX_API = "https://api.storekit-sandbox.apple.com";

    private final RestClient bookApiRestClient;
    private final Clock clock;

    @SuppressWarnings("unchecked")
    public AppStoreTransaction getTransaction(BookeyProperties.Payment.Apple config, String transactionId) {
        try {
            Map<String, Object> response = bookApiRestClient.get()
                    .uri(baseUrl(config) + "/inApps/v1/transactions/" + transactionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken(config))
                    .retrieve()
                    .body(Map.class);
            Object signed = response == null ? null : response.get("signedTransactionInfo");
            if (!(signed instanceof String signedInfo) || signedInfo.isBlank()) {
                throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
            }
            Map<String, Object> payload = decodeJwsPayload(signedInfo);
            return new AppStoreTransaction(
                    string(payload.get("transactionId")),
                    string(payload.get("originalTransactionId")),
                    string(payload.get("productId")),
                    string(payload.get("bundleId")),
                    longValue(payload.get("expiresDate")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "App Store 거래를 검증하지 못했습니다.");
        }
    }

    private String bearerToken(BookeyProperties.Payment.Apple config) throws Exception {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(config.issuerId())
                .audience().add("appstoreconnect-v1").and()
                .claim("bid", config.bundleId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .header().keyId(config.keyId()).and()
                .signWith(privateKey(config.privateKey()), Jwts.SIG.ES256)
                .compact();
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static String baseUrl(BookeyProperties.Payment.Apple config) {
        return "PRODUCTION".equalsIgnoreCase(config.environment()) ? PROD_API : SANDBOX_API;
    }

    private static Map<String, Object> decodeJwsPayload(String jws) {
        String[] parts = jws.split("\\.");
        if (parts.length < 2) {
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(parts[1]);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(bytes, Map.class);
        } catch (Exception e) {
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public record AppStoreTransaction(String transactionId, String originalTransactionId, String productId,
                                      String bundleId, long expiresDateMillis) {}
}
