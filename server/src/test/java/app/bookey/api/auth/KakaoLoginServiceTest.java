package app.bookey.api.auth;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 카카오 로그인 중계 — 서버가 코드를 토큰으로 바꾸고, 교환 코드는 code_verifier 를 가진 앱만 한 번 쓴다. */
class KakaoLoginServiceTest {

    private static final String APP = "bookey://auth/kakao";
    private static final String CALLBACK = "https://api.bookey.site/api/v1/auth/kakao/callback";
    private static final String VERIFIER = "verifier-0123456789-0123456789-0123456789-abc";
    private static final String STATE = "app-state-1234";

    private final Map<String, String> store = new HashMap<>();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer kakao = MockRestServiceServer.bindTo(builder).build();
    private final RateLimiter rateLimiter = mock(RateLimiter.class);

    private KakaoLoginService service(String restKey) {
        return service(restKey, 1234L);
    }

    @SuppressWarnings("unchecked")
    private KakaoLoginService service(String restKey, Long appId) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.getAndDelete(anyString())).thenAnswer(inv -> store.remove(inv.<String>getArgument(0)));
        BookeyProperties properties = new BookeyProperties(null, null, null,
                new BookeyProperties.OAuth(null, null, appId,
                        new BookeyProperties.OAuth.KakaoLogin(restKey, "secret-1", CALLBACK, List.of(APP))),
                null, null, null, null, null, null);
        return new KakaoLoginService(redis, new ObjectMapper(), builder.build(), properties, rateLimiter);
    }

    private static String challengeOf(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static Map<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
    }

    @Test
    @DisplayName("카카오 화면 → 콜백에서 시크릿으로 코드 교환 → 앱 주소로 교환 코드 → code_verifier 로 토큰을 한 번만 받는다")
    void fullFlow() throws Exception {
        KakaoLoginService service = service("rest-key");

        URI toKakao = service.authorize(APP, STATE, challengeOf(VERIFIER), "S256", "ip:1");
        assertThat(toKakao.toString()).startsWith("https://kauth.kakao.com/oauth/authorize?");
        Map<String, String> kakaoQuery = query(toKakao);
        assertThat(kakaoQuery).containsEntry("client_id", "rest-key").containsEntry("response_type", "code");
        assertThat(java.net.URLDecoder.decode(kakaoQuery.get("redirect_uri"), StandardCharsets.UTF_8)).isEqualTo(CALLBACK);
        String nonce = kakaoQuery.get("state");
        assertThat(nonce).isNotEqualTo(STATE);

        kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code", "client_id", "rest-key",
                        "redirect_uri", CALLBACK, "code", "kakao-code", "client_secret", "secret-1")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        URI toApp = service.callback("kakao-code", nonce, null);
        kakao.verify();
        assertThat(toApp.toString()).startsWith(APP + "?");
        Map<String, String> appQuery = query(toApp);
        assertThat(appQuery).containsEntry("state", STATE).doesNotContainKey("error");

        assertThat(service.exchange(appQuery.get("code"), VERIFIER)).isEqualTo("kakao-access");
        assertApiError(() -> service.exchange(appQuery.get("code"), VERIFIER), ErrorCode.INVALID_TOKEN);
        // 같은 state 로 콜백을 다시 부르면 맡긴 기록이 없어 기본 앱 주소로 돌려보낸다
        assertThat(query(service.callback("kakao-code", nonce, null))).containsEntry("error", "expired");
    }

    @Test
    @DisplayName("교환 코드를 가로채도 code_verifier 가 다르면 토큰을 주지 않고, 그 교환 코드는 사라진다")
    void wrongVerifierBurnsTicket() throws Exception {
        KakaoLoginService service = service("rest-key");
        String nonce = query(service.authorize(APP, STATE, challengeOf(VERIFIER), "S256", "ip:1")).get("state");
        kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
        String ticket = query(service.callback("kakao-code", nonce, null)).get("code");

        assertApiError(() -> service.exchange(ticket, "attacker-0123456789-0123456789-0123456789-xyz"), ErrorCode.INVALID_TOKEN);
        assertApiError(() -> service.exchange(ticket, VERIFIER), ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("허용하지 않은 앱 주소·PKCE 없는 요청은 카카오로 보내지 않는다")
    void rejectsUnknownRedirectAndPlainChallenge() throws Exception {
        KakaoLoginService service = service("rest-key");
        String challenge = challengeOf(VERIFIER);

        assertApiError(() -> service.authorize("https://evil.example/cb", STATE, challenge, "S256", "ip:1"), ErrorCode.INVALID_REQUEST);
        assertApiError(() -> service.authorize(APP + "/more", STATE, challenge, "S256", "ip:1"), ErrorCode.INVALID_REQUEST);
        assertApiError(() -> service.authorize(APP, STATE, challenge, "plain", "ip:1"), ErrorCode.INVALID_REQUEST);
        assertApiError(() -> service.authorize(APP, "short", challenge, "S256", "ip:1"), ErrorCode.INVALID_REQUEST);
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 카카오로 보내지 않고 앱에 '쓸 수 없음'을 돌려준다")
    void unavailableWithoutKey() throws Exception {
        URI toApp = service("").authorize(APP, STATE, challengeOf(VERIFIER), "S256", "ip:1");

        assertThat(toApp.toString()).startsWith(APP + "?");
        assertThat(query(toApp)).containsEntry("error", "unavailable").containsEntry("state", STATE);
    }

    @Test
    @DisplayName("카카오 앱 ID 가 없으면 남의 앱 토큰을 걸러 낼 수 없어 카카오로 보내지 않는다")
    void unavailableWithoutAppId() throws Exception {
        URI toApp = service("rest-key", null).authorize(APP, STATE, challengeOf(VERIFIER), "S256", "ip:1");

        assertThat(query(toApp)).containsEntry("error", "unavailable");
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("같은 IP 가 너무 자주 시작하면 RATE_LIMITED — 맡기는 기록도 카카오 호출도 늘지 않는다")
    void rateLimitedPerIp() throws Exception {
        doThrow(ApiException.of(ErrorCode.RATE_LIMITED)).when(rateLimiter)
                .require(eq("kakao-authorize:ip:9"), eq(20), any(Duration.class));
        KakaoLoginService service = service("rest-key");
        String challenge = challengeOf(VERIFIER);

        assertApiError(() -> service.authorize(APP, STATE, challenge, "S256", "ip:9"), ErrorCode.RATE_LIMITED);
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("동의 화면에서 취소하면 코드 교환 없이 access_denied 를 앱에 돌려준다")
    void cancelGoesBackToApp() throws Exception {
        KakaoLoginService service = service("rest-key");
        String nonce = query(service.authorize(APP, STATE, challengeOf(VERIFIER), "S256", "ip:1")).get("state");

        Map<String, String> appQuery = query(service.callback(null, nonce, "access_denied"));

        assertThat(appQuery).containsEntry("error", "access_denied").containsEntry("state", STATE);
        kakao.verify();
    }

    private static void assertApiError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(code);
    }
}
