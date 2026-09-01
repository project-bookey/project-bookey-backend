package app.bookey.domain.challenge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingChallengeTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    private ReadingChallenge challenge(int budgetSec) {
        ReadingChallenge c = ReadingChallenge.builder()
                .userId(1L).readingRecordId(10L).budgetSec(budgetSec).build();
        c.resume(T0); // 생성 즉시 시작
        return c;
    }

    @Test
    @DisplayName("타임워치가 도는 동안만 시간이 소모된다")
    void elapsedCountsOnlyWhileRunning() {
        ReadingChallenge c = challenge(3600);
        assertThat(c.effectiveElapsedSec(T0.plusSeconds(100))).isEqualTo(100);

        c.pause(T0.plusSeconds(100));
        assertThat(c.effectiveElapsedSec(T0.plusSeconds(500))).isEqualTo(100); // 멈춤 동안 고정

        c.resume(T0.plusSeconds(500));
        assertThat(c.effectiveElapsedSec(T0.plusSeconds(700))).isEqualTo(300); // 100 + 200
        assertThat(c.remainingSec(T0.plusSeconds(700))).isEqualTo(3300);
    }

    @Test
    @DisplayName("일시정지·재개는 멱등이다")
    void pauseAndResumeAreIdempotent() {
        ReadingChallenge c = challenge(3600);
        c.pause(T0.plusSeconds(50));
        c.pause(T0.plusSeconds(80)); // 두 번째 pause는 무해
        assertThat(c.effectiveElapsedSec(T0.plusSeconds(80))).isEqualTo(50);

        c.resume(T0.plusSeconds(100));
        c.resume(T0.plusSeconds(150)); // 두 번째 resume은 구간 시작을 덮지 않는다
        assertThat(c.effectiveElapsedSec(T0.plusSeconds(200))).isEqualTo(150); // 50 + 100
    }

    @Test
    @DisplayName("예산 소진 시 만료되고, fail은 경과를 예산으로 고정한다")
    void failCapsElapsedAtBudget() {
        ReadingChallenge c = challenge(100);
        assertThat(c.isExpired(T0.plusSeconds(99))).isFalse();
        assertThat(c.isExpired(T0.plusSeconds(100))).isTrue();

        c.fail(T0.plusSeconds(250));
        assertThat(c.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(c.isRunning()).isFalse();
        assertThat(c.getElapsedSec()).isEqualTo(100); // 예산 초과분은 버림
        assertThat(c.getCompletedAt()).isEqualTo(T0.plusSeconds(250));
    }

    @Test
    @DisplayName("성공 전이는 타임워치를 멈추고 경과를 확정한다")
    void succeedStopsWatch() {
        ReadingChallenge c = challenge(3600);
        c.succeed(T0.plusSeconds(1200));
        assertThat(c.getStatus()).isEqualTo(ChallengeStatus.SUCCEEDED);
        assertThat(c.isRunning()).isFalse();
        assertThat(c.getElapsedSec()).isEqualTo(1200);
        assertThat(c.remainingSec(T0.plusSeconds(9999))).isEqualTo(2400); // 종료 후 고정
    }
}
