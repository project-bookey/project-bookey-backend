package app.bookey.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final RateLimiter limiter = new RateLimiter(redis);

    @Test
    @DisplayName("acquire — 첫 호출은 윈도우를 걸고, 남은 횟수는 limit 에서 지금까지 센 수를 뺀 값")
    void acquireCountsDown() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("rl:k")).thenReturn(1L, 10L);

        assertThat(limiter.acquire("k", 10, Duration.ofHours(1))).isEqualTo(new RateLimiter.Permit(true, 9));
        verify(redis).expire("rl:k", Duration.ofHours(1));
        assertThat(limiter.acquire("k", 10, Duration.ofHours(1))).isEqualTo(new RateLimiter.Permit(true, 0));
    }

    @Test
    @DisplayName("acquire — limit 을 넘으면 막는다")
    void acquireOverLimit() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("rl:k")).thenReturn(11L);

        assertThat(limiter.acquire("k", 10, Duration.ofHours(1))).isEqualTo(new RateLimiter.Permit(false, 0));
        assertThat(limiter.tryAcquire("k", 10, Duration.ofHours(1))).isFalse();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("acquire — Redis 를 못 쓰면 막지 않고, 남은 횟수는 모른다(null)")
    void acquireWithoutRedis() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("rl:k")).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.acquire("k", 10, Duration.ofHours(1))).isEqualTo(new RateLimiter.Permit(true, null));
        assertThat(limiter.tryAcquire("k", 10, Duration.ofHours(1))).isTrue();
    }
}
