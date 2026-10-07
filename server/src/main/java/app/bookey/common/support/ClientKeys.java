package app.bookey.common.support;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 비회원 요청을 구분하는 열람자 키와 봇 판별 — 공개 웹(www.bookey.site)이 넘기는 비회원 호출에 쓴다.
 *
 * <p>원문 IP 는 Redis 키에 남기지 않고 SHA-256 앞 16자리만 쓴다. nginx·공개 웹 서버 뒤에서는
 * {@code server.forward-headers-strategy=native} 가 X-Forwarded-For 를 풀어 {@code getRemoteAddr()} 가
 * 실제 방문자 IP 가 된다(application-prod.yml).
 */
public final class ClientKeys {

    /** 검색엔진·링크 미리보기 봇 — 이들의 열람은 조회수로 세지 않는다. */
    private static final Pattern BOT = Pattern.compile(
            "googlebot|bingbot|yeti|daum|kakaotalk-scrap|facebookexternalhit|twitterbot|slackbot"
                    + "|discordbot|linkedinbot|applebot|duckduckbot|baiduspider|yandexbot",
            Pattern.CASE_INSENSITIVE);

    private ClientKeys() {
    }

    /** 요청을 보낸 주소로 만든 열람자 키 — {@code ip:} + SHA-256 앞 16자리. */
    public static String ipKey(HttpServletRequest request) {
        return ipKey(request.getRemoteAddr());
    }

    public static String ipKey(String remoteAddr) {
        return "ip:" + sha256Hex(remoteAddr == null ? "" : remoteAddr).substring(0, 16);
    }

    /** User-Agent 가 알려진 봇이면 true. 없으면 사람으로 본다. */
    public static boolean isBot(String userAgent) {
        return userAgent != null && BOT.matcher(userAgent).find();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없어요.", e);
        }
    }
}
