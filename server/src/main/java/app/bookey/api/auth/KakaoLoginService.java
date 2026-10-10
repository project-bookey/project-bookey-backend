package app.bookey.api.auth;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.config.BookeyProperties.OAuth.KakaoLogin;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * 카카오 로그인 중계. 카카오는 리다이렉트 URI 로 http(s)만 받고 새 REST 키는 클라이언트 시크릿이 켜져 있어,
 * 앱이 bookey:// 로 코드를 받아 직접 토큰으로 바꿀 수 없다. 그래서 서버가 앱 대신 OAuth 를 한 바퀴 돈다.
 *  1. authorize — 앱이 연 로그인 창을 카카오 로그인 화면으로 보낸다. 앱의 복귀 주소·state·code_challenge 는 Redis 에 맡긴다.
 *  2. callback — 카카오가 돌려준 코드를 시크릿과 함께 토큰으로 바꾸고, 한 번 쓰는 교환 코드를 붙여 앱 주소로 돌려보낸다.
 *  3. exchange — 앱이 교환 코드와 code_verifier 를 보내면 카카오 액세스 토큰을 내준다. 앱은 그 토큰으로 /auth/social 을 부른다.
 * 교환 코드는 PKCE(S256)로 로그인을 시작한 앱에 묶여, 남이 시작한 로그인의 bookey:// 주소를 가로챈 앱은 토큰을 받지 못한다.
 * 다만 bookey:// 는 어느 앱이든 받을 수 있는 주소라, 기기에 깔린 악성 앱이 로그인을 직접 시작하는 것까지는 막지 못한다
 * (iOS 는 그 앱 이름으로 로그인 확인 창을 띄운다). 이것까지 막으려면 앱 주소를 Universal Links·App Links 로 검증된 https 로 바꿔야 한다.
 * 카카오 앱 ID(KAKAO_APP_ID)가 없으면 남의 앱 토큰을 걸러 내지 못하므로 카카오 로그인을 켜지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String AUTHORIZE = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN = "https://kauth.kakao.com/oauth/token";
    private static final String STATE_PREFIX = "auth:kakao:state:";
    private static final String TICKET_PREFIX = "auth:kakao:ticket:";
    /** 설정이 비었을 때 돌려보낼 앱 주소. */
    private static final String DEFAULT_APP_REDIRECT = "bookey://auth/kakao";
    /** 카카오 로그인 화면에 머물 수 있는 시간 — 처음 동의하며 항목을 읽는 시간까지. */
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    /** 앱은 복귀 주소를 받자마자 교환하므로 짧게 둔다. */
    private static final Duration TICKET_TTL = Duration.ofMinutes(3);
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9._~-]{8,128}");
    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    /** 로그인 없이 부르는 시작 주소라 IP 마다 분당 횟수를 묶는다 — 콜백은 시작 한 번에 한 번만 카카오를 부르므로 함께 묶인다. */
    private static final int AUTHORIZE_LIMIT_PER_MINUTE = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;
    private final RateLimiter rateLimiter;

    /** 로그인을 맡길 때 기억해 두는 앱 쪽 값. */
    record Pending(String appRedirect, String state, String challenge) {}

    /** 교환 코드로 내줄 카카오 토큰과, 교환할 앱을 확인할 code_challenge. */
    record Ticket(String accessToken, String challenge) {}

    /** 1. 카카오 로그인 화면 주소. 설정이 없거나 맡길 수 없으면 앱 주소에 error 를 붙여 돌려보낸다. clientKey 는 요청 IP 키. */
    public URI authorize(String appRedirect, String state, String challenge, String challengeMethod, String clientKey) {
        if (!appRedirects().contains(appRedirect) || state == null || !STATE.matcher(state).matches()
                || challenge == null || !CHALLENGE.matcher(challenge).matches() || !"S256".equals(challengeMethod)) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        rateLimiter.require("kakao-authorize:" + clientKey, AUTHORIZE_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
        KakaoLogin kakao = properties.oauth().kakaoLogin();
        if (kakao == null || isBlank(kakao.restKey()) || properties.oauth().kakaoAppId() == null) {
            return withQuery(appRedirect, "error", "unavailable", "state", state);
        }
        String nonce = randomToken();
        if (!put(STATE_PREFIX + nonce, new Pending(appRedirect, state, challenge), STATE_TTL)) {
            return withQuery(appRedirect, "error", "unavailable", "state", state);
        }
        return withQuery(AUTHORIZE,
                "response_type", "code",
                "client_id", kakao.restKey(),
                "redirect_uri", kakao.redirectUri(),
                "state", nonce);
    }

    /** 2. 카카오가 돌려보낸 곳. 무엇이 잘못돼도 앱 주소로 돌려보내 열린 로그인 창을 닫게 한다. */
    public URI callback(String code, String nonce, String error) {
        Pending pending = take(STATE_PREFIX, nonce, Pending.class);
        if (pending == null) {
            // 맡긴 기록이 없으면(만료·재사용) 어느 앱 주소인지 모른다 — 기본 앱 주소로 보낸다.
            return withQuery(appRedirects().getFirst(), "error", "expired");
        }
        if (error != null || isBlank(code)) {
            // 사용자가 동의 화면에서 취소하면 access_denied 가 온다.
            String reason = "access_denied".equals(error) ? "access_denied" : "failed";
            return withQuery(pending.appRedirect(), "error", reason, "state", pending.state());
        }
        String accessToken;
        try {
            accessToken = exchangeCode(code);
        } catch (Exception e) {
            log.warn("Kakao token exchange failed", e);
            return withQuery(pending.appRedirect(), "error", "failed", "state", pending.state());
        }
        String ticket = randomToken();
        if (!put(TICKET_PREFIX + ticket, new Ticket(accessToken, pending.challenge()), TICKET_TTL)) {
            return withQuery(pending.appRedirect(), "error", "unavailable", "state", pending.state());
        }
        return withQuery(pending.appRedirect(), "code", ticket, "state", pending.state());
    }

    /** 3. 교환 코드 + code_verifier → 카카오 액세스 토큰. 교환 코드는 맞든 틀리든 한 번만 쓴다. */
    public String exchange(String ticket, String codeVerifier) {
        Ticket saved = take(TICKET_PREFIX, ticket, Ticket.class);
        if (saved == null || codeVerifier == null || !VERIFIER.matcher(codeVerifier).matches()
                || !MessageDigest.isEqual(challengeOf(codeVerifier), saved.challenge().getBytes(StandardCharsets.US_ASCII))) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
        return saved.accessToken();
    }

    @SuppressWarnings("unchecked")
    private String exchangeCode(String code) {
        KakaoLogin kakao = properties.oauth().kakaoLogin();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakao.restKey());
        form.add("redirect_uri", kakao.redirectUri());
        form.add("code", code);
        if (!isBlank(kakao.clientSecret())) {
            form.add("client_secret", kakao.clientSecret());
        }
        Map<String, Object> body = bookApiRestClient.post()
                .uri(TOKEN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object token = body == null ? null : body.get("access_token");
        if (!(token instanceof String accessToken) || accessToken.isBlank()) {
            throw new IllegalStateException("Kakao token response has no access_token");
        }
        return accessToken;
    }

    private List<String> appRedirects() {
        KakaoLogin kakao = properties.oauth().kakaoLogin();
        List<String> allowed = kakao == null || kakao.appRedirects() == null ? List.of()
                : kakao.appRedirects().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        return allowed.isEmpty() ? List.of(DEFAULT_APP_REDIRECT) : allowed;
    }

    private boolean put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            return true;
        } catch (DataAccessException e) {
            log.warn("Kakao login unavailable; Redis write failed");
            return false;
        }
    }

    /** 맡긴 값을 꺼내며 지운다 — 같은 state·교환 코드를 두 번 쓰지 못하게. */
    private <T> T take(String prefix, String id, Class<T> type) {
        if (isBlank(id) || id.length() > 128) {
            return null;
        }
        try {
            String json = redis.opsForValue().getAndDelete(prefix + id);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (DataAccessException e) {
            log.warn("Kakao login unavailable; Redis read failed");
            return null;
        }
    }

    private static byte[] challengeOf(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** base 에 key=value 쌍을 쿼리로 붙인다(값은 URL 인코딩). */
    private static URI withQuery(String base, String... pairs) {
        StringJoiner query = new StringJoiner("&");
        for (int i = 0; i < pairs.length; i += 2) {
            query.add(pairs[i] + "=" + URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return URI.create(base + (base.contains("?") ? "&" : "?") + query);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
