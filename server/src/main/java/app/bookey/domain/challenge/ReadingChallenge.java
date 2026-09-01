package app.bookey.domain.challenge;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 챌린지 — 독서시간 예산 타임워치. 시간의 진실은 서버:
 * running 구간의 경과는 last_started_at과 now의 차로 계산한다 (§F3 철학).
 */
@Getter
@Entity
@Table(name = "reading_challenges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingChallenge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reading_record_id", nullable = false)
    private Long readingRecordId;

    @Column(name = "budget_sec", nullable = false)
    private int budgetSec;

    @Column(name = "elapsed_sec", nullable = false)
    private int elapsedSec;

    @Column(nullable = false)
    private boolean running;

    @Column(name = "last_started_at")
    private Instant lastStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeStatus status = ChallengeStatus.ACTIVE;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private ReadingChallenge(Long userId, Long readingRecordId, int budgetSec) {
        this.userId = userId;
        this.readingRecordId = readingRecordId;
        this.budgetSec = budgetSec;
        this.status = ChallengeStatus.ACTIVE;
    }

    /** 확정 누적 + (도는 중이면) 이번 구간 경과. */
    public int effectiveElapsedSec(Instant now) {
        long base = elapsedSec;
        if (running && lastStartedAt != null) {
            base += Math.max(0, Duration.between(lastStartedAt, now).getSeconds());
        }
        return (int) Math.min(Integer.MAX_VALUE, base);
    }

    public int remainingSec(Instant now) {
        return Math.max(0, budgetSec - effectiveElapsedSec(now));
    }

    public boolean isExpired(Instant now) {
        return effectiveElapsedSec(now) >= budgetSec;
    }

    /** 재개 — 이미 도는 중이면 무해(구간 시작을 덮지 않는다). */
    public void resume(Instant now) {
        if (!running) {
            running = true;
            lastStartedAt = now;
        }
    }

    /** 일시정지 — 경과 확정. 이미 멈춰 있으면 무해. */
    public void pause(Instant now) {
        if (running) {
            elapsedSec = effectiveElapsedSec(now);
            running = false;
            lastStartedAt = null;
        }
    }

    /** 만료 실패 — 경과는 예산으로 고정한다. */
    public void fail(Instant now) {
        pause(now);
        elapsedSec = Math.min(elapsedSec, budgetSec);
        status = ChallengeStatus.FAILED;
        completedAt = now;
    }

    public void succeed(Instant now) {
        pause(now);
        status = ChallengeStatus.SUCCEEDED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        pause(now);
        status = ChallengeStatus.CANCELLED;
        completedAt = now;
    }
}
