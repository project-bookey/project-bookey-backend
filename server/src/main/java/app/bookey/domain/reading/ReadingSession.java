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

    /** 타이머 자동 종료 기준 (§F3). */
    public static final Duration MAX_SESSION = Duration.ofHours(4);

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
        Duration elapsed = Duration.between(startedAt, actualEnd);
        if (elapsed.isNegative()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "끝난 시간이 시작 시간보다 빨라요.");
        }
        if (elapsed.compareTo(MAX_SESSION) > 0) {
            // 4시간 초과 세션은 자동 종료 취급 (§F3)
            elapsed = MAX_SESSION;
            actualEnd = startedAt.plus(MAX_SESSION);
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
