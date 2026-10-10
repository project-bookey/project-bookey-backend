package app.bookey.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessRevocationsTest {

    private static final Instant AT = Instant.parse("2026-10-10T03:00:00Z");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final AccessRevocations revocations = new AccessRevocations(redis, tokenProvider);

    @Test
    @DisplayName("폐기 시각을 토큰 수명보다 조금 길게 기록한다")
    void revokeStoresUntilTokensExpire() {
        when(redis.opsForValue()).thenReturn(ops);
        when(tokenProvider.accessTtl()).thenReturn(Duration.ofHours(1));

        revocations.revoke(7L, AT);

        verify(ops).set("auth:revoked:7", String.valueOf(AT.getEpochSecond()), Duration.ofMinutes(61));
    }

    @Test
    @DisplayName("폐기 시각과 같은 초까지 발급된 토큰은 막고, 그 뒤에 발급된 토큰은 통과시킨다")
    void revokedUpToTheSecond() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("auth:revoked:7")).thenReturn(String.valueOf(AT.getEpochSecond()));

        assertThat(revocations.isRevoked(7L, AT.minusSeconds(30))).isTrue();
        assertThat(revocations.isRevoked(7L, AT.plusMillis(500))).isTrue();
        assertThat(revocations.isRevoked(7L, AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("기록이 없거나 Redis 를 못 쓰면 막지 않는다")
    void failOpen() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("auth:revoked:7")).thenReturn(null);
        assertThat(revocations.isRevoked(7L, AT)).isFalse();

        when(ops.get("auth:revoked:8")).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(revocations.isRevoked(8L, AT)).isFalse();
    }
}
