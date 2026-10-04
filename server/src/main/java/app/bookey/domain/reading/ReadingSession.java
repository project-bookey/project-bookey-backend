package app.bookey.domain.reading;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "reading_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingSession {

    /** 타이머 자동 종료 기준 (§F3) — 쉰 시간을 뺀 독서 시간으로 잰다. */
    public static final Duration MAX_SESSION = Duration.ofHours(4);

    /** 이보다 오래 쉬고 있는 세션은 쉬기 시작한 시각에 끝난 것으로 보고 닫는다. */
    public static final Duration MAX_PAUSE = Duration.ofHours(4);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reading_record_id", nullable = false)
    private Long readingRecordId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "start_page")
    private Integer startPage;

    @Column(name = "end_page")
    private Integer endPage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionSource source = SessionSource.TIMER;

    @Column(name = "foreground_ratio", precision = 4, scale = 3)
    private BigDecimal foregroundRatio;

    @Column(name = "interaction_count", nullable = false)
    private int interactionCount;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "client_uuid")
    private UUID clientUuid;

    /** 잠깐 쉬기 시작한 시각 — 쉬는 중일 때만 있다. */
    @Column(name = "paused_at")
    private Instant pausedAt;

    /** 지금까지 쉰 시간(초). 지금 쉬고 있는 몫은 이어서 읽거나 끝낼 때 더한다. */
    @Column(name = "paused_sec", nullable = false)
    private int pausedSec;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Builder
    private ReadingSession(Long readingRecordId, Long userId, Instant startedAt, Integer startPage,
                           SessionSource source, UUID clientUuid) {
        this.readingRecordId = readingRecordId;
        this.userId = userId;
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.startPage = startPage;
        this.source = source == null ? SessionSource.TIMER : source;
        this.clientUuid = clientUuid;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    /** 잠깐 쉬기. 이미 쉬는 중이면 그대로 둔다. */
    public void pause(Instant now) {
        if (!isOpen()) {
            throw ApiException.of(ErrorCode.SESSION_ALREADY_CLOSED);
        }
        if (isPaused()) {
            return;
        }
        this.pausedAt = now.isBefore(startedAt) ? startedAt : now;
    }

    /** 이어서 읽기 — 쉰 시간을 쌓아 두고 다시 잰다. 쉬는 중이 아니면 그대로 둔다. */
    public void resume(Instant now) {
        if (!isOpen()) {
            throw ApiException.of(ErrorCode.SESSION_ALREADY_CLOSED);
        }
        foldPause(now);
    }

    /** 지금 읽는 중인가 — 열려 있고, 쉬지 않고, 독서 시간이 아직 4시간 안쪽. */
    public boolean isReadingNow(Instant now) {
        return isOpen() && !isPaused() && activeDuration(now).compareTo(MAX_SESSION) < 0;
    }

    /** 쉰 시간을 뺀 독서 시간. */
    public Duration activeDuration(Instant at) {
        Duration paused = Duration.ofSeconds(pausedSec);
        if (isPaused() && at.isAfter(pausedAt)) {
            paused = paused.plus(Duration.between(pausedAt, at));
        }
        Duration active = Duration.between(startedAt, at).minus(paused);
        return active.isNegative() ? Duration.ZERO : active;
    }

    /** 지금 쉬고 있는 몫을 쉰 시간에 더하고 쉬기를 끝낸다. */
    private void foldPause(Instant now) {
        if (!isPaused()) {
            return;
        }
        if (now.isAfter(pausedAt)) {
            this.pausedSec += (int) Duration.between(pausedAt, now).getSeconds();
        }
        this.pausedAt = null;
    }

    /**
     * 세션 종료. 한 번에 많이 읽거나 빨리 읽어도 그대로 인정한다 — 어뷰징 감지는 없앴다(2026-10-05, 사용자 결정).
     *
     * @param endedAt          종료 시각
     * @param endPage          종료 시점 페이지
     * @param foregroundRatio  앱 포그라운드 유지 비율
     * @param interactionCount 상호작용 횟수
     */
    public void close(Instant endedAt, Integer endPage, Double foregroundRatio,
                      Integer interactionCount, String memo) {
        if (!isOpen()) {
            throw ApiException.of(ErrorCode.SESSION_ALREADY_CLOSED);
        }
        Instant actualEnd = endedAt == null ? Instant.now() : endedAt;
        if (actualEnd.isBefore(startedAt)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "끝난 시간이 시작 시간보다 빨라요.");
        }
        Duration elapsed = activeDuration(actualEnd);
        foldPause(actualEnd);
        if (elapsed.compareTo(MAX_SESSION) > 0) {
            // 쉰 시간을 빼고도 4시간을 넘긴 세션은 4시간을 채운 때에 끝난 것으로 본다 (§F3)
            actualEnd = actualEnd.minus(elapsed.minus(MAX_SESSION));
            elapsed = MAX_SESSION;
        }
        this.endedAt = actualEnd;
        this.durationSec = (int) elapsed.getSeconds();
        this.endPage = endPage;
        this.foregroundRatio = foregroundRatio == null ? null : BigDecimal.valueOf(foregroundRatio);
        this.interactionCount = interactionCount == null ? 0 : interactionCount;
        this.memo = memo;
    }

    /** 수동 기록은 사후 입력이므로 생성과 동시에 종료 상태로 만든다. */
    public void closeManual(Instant endedAt, int durationSec, Integer endPage, String memo) {
        this.endedAt = endedAt;
        this.durationSec = durationSec;
        this.endPage = endPage;
        this.memo = memo;
        this.source = SessionSource.MANUAL;
    }

    /** 이 세션에서 읽은 페이지 수. */
    public Integer readPages() {
        if (startPage == null || endPage == null) {
            return null;
        }
        int diff = endPage - startPage;
        return diff > 0 ? diff : 0;
    }

    public int verifiedDurationSec() {
        return (int) Math.round(durationSec * source.verificationWeight());
    }
}
