package app.bookey.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 이미 발급된 사용자 access token 을 만료 전에 끊는다. 정지·영구정지·탈퇴 직후에도 남은 토큰(최대 1시간)으로
 * API 를 계속 쓰는 것을 막는다. 끊긴 토큰은 401 을 받고, 앱은 refresh 를 시도하다 정지 사유(USER_SUSPENDED)를 받아
 * 로그아웃한다. Redis 를 못 쓰면 막지 않고 통과시킨다 — 토큰 수명이 지나면 어차피 끊긴다(RateLimiter 와 같은 선택).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccessRevocations {

    private static final String PREFIX = "auth:revoked:";
    /** 폐기 시각과 같은 초에 발급된 토큰까지 막으므로, 기록은 토큰 수명보다 조금 길게 둔다. */
    private static final Duration MARGIN = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final JwtTokenProvider tokenProvider;

    /** at 까지 발급된 이 사용자의 access token 을 모두 무효로 한다. */
    public void revoke(Long userId, Instant at) {
        try {
            redis.opsForValue().set(PREFIX + userId, String.valueOf(at.getEpochSecond()),
                    tokenProvider.accessTtl().plus(MARGIN));
        } catch (DataAccessException e) {
            log.warn("Access revocation unavailable; tokens expire on their own: userId={}", userId);
        }
    }

    /** 제재가 풀리면 기록을 지운다 — 다시 로그인한 토큰이 같은 초에 발급돼도 막히지 않게. */
    public void clear(Long userId) {
        try {
            redis.delete(PREFIX + userId);
        } catch (DataAccessException e) {
            log.warn("Access revocation unavailable; could not clear: userId={}", userId);
        }
    }

    public boolean isRevoked(Long userId, Instant issuedAt) {
        if (issuedAt == null) {
            return false;
        }
        try {
            String value = redis.opsForValue().get(PREFIX + userId);
            return value != null && issuedAt.getEpochSecond() <= Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            return false;
        }
    }
}
