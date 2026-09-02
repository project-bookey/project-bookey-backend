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

    /** 허용되면 true. 윈도우 내 호출 수가 limit을 넘으면 false. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = "rl:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            return count != null && count <= limit;
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate limiter unavailable; allowing request: key={}", key);
            return true;
        }
    }

    public void require(String key, int limit, Duration window) {
        if (!tryAcquire(key, limit, window)) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
