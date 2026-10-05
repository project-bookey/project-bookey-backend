package app.bookey.common.support;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis 고정 윈도우 레이트리밋. 초대 코드 무작위 대입 방어 등에 사용(§8.5). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redis;

    /**
     * 이번 호출이 허용됐는지와, 허용됐다면 윈도우 안에서 더 부를 수 있는 횟수.
     * Redis 를 못 써서 막지 않고 통과시켰을 땐 남은 횟수를 모르니 remaining 이 null 이다.
     */
    public record Permit(boolean allowed, Integer remaining) {}

    /** 윈도우 내 호출 수를 하나 올리고, limit 을 넘었는지와 남은 횟수를 돌려준다. */
    public Permit acquire(String key, int limit, Duration window) {
        String redisKey = "rl:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            if (count == null || count > limit) {
                return new Permit(false, 0);
            }
            return new Permit(true, (int) (limit - count));
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate limiter unavailable; allowing request: key={}", key);
            return new Permit(true, null);
        }
    }

    /** 허용되면 true. 윈도우 내 호출 수가 limit을 넘으면 false. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        return acquire(key, limit, window).allowed();
    }

    public void require(String key, int limit, Duration window) {
        if (!tryAcquire(key, limit, window)) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
