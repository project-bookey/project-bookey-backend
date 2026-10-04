package app.bookey.api.social.payment;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Google Play Developer API. 기기에서 받은 purchase token을 서버에서 다시 검증한다. */
@Component
@RequiredArgsConstructor
public class GooglePlayPaymentClient {
    private static final String API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/";
    private static final String TOKEN_API = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

    private final RestClient bookApiRestClient;
    private final Clock clock;

    @SuppressWarnings("unchecked")
    public GoogleSubscription getSubscription(BookeyProperties.Payment.Google config, String token) {
        try {
            Map<String, Object> body = get(config, "/purchases/subscriptionsv2/tokens/" + enc(token));
            String state = string(body.get("subscriptionState"));
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("lineItems");
            if (items == null || items.isEmpty()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
            Map<String, Object> item = items.get(0);
            return new GoogleSubscription(string(item.get("productId")), state,
                    Instant.parse(string(item.get("expiryTime"))));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Google Play 구독을 검증하지 못했습니다.");
        }
    }

    public GoogleProduct getProduct(BookeyProperties.Payment.Google config, String productId, String token) {
        try {
            Map<String, Object> body = get(config, "/purchases/products/" + enc(productId) + "/tokens/" + enc(token));
            return new GoogleProduct(number(body.get("purchaseState")), string(body.get("orderId")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Google Play 구매를 검증하지 못했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(BookeyProperties.Payment.Google config, String path) throws Exception {
        requireConfigured(config);
        String bearer = accessToken(config);
        return bookApiRestClient.get()
                .uri(API + enc(config.packageName()) + path)
                .headers(h -> h.setBearerAuth(bearer))
                .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private String accessToken(BookeyProperties.Payment.Google config) throws Exception {
        Instant now = clock.instant();
        String assertion = Jwts.builder()
                .issuer(config.serviceAccountEmail()).subject(config.serviceAccountEmail())
                .audience().add(TOKEN_API).and().claim("scope", SCOPE)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(privateKey(config.privateKey()), Jwts.SIG.RS256).compact();
        String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + enc(assertion);
        Map<String, Object> response = bookApiRestClient.post().uri(TOKEN_API)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Map.class);
        String token = response == null ? "" : string(response.get("access_token"));
        if (token.isBlank()) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        return token;
    }

    private static void requireConfigured(BookeyProperties.Payment.Google config) {
        if (config == null || blank(config.packageName()) || blank(config.serviceAccountEmail()) || blank(config.privateKey()))
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n").replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(string(value)); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record GoogleSubscription(String productId, String state, Instant expiresAt) {}
    public record GoogleProduct(int purchaseState, String orderId) {}
}
