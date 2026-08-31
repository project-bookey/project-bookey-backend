package app.bookey.domain.reading;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "reading_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** 재독 회차 (§F2). */
    @Column(nullable = false)
    private short round = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadingStatus status = ReadingStatus.WANT_TO_READ;

    @Column(name = "current_page", nullable = false)
    private int currentPage;

    @Column(name = "total_pages_override")
    private Integer totalPagesOverride;

    @Column(name = "target_finish_date")
    private LocalDate targetFinishDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "abandon_reason", length = 30)
    private AbandonReason abandonReason;

    private Short rating;

    @Builder
    private ReadingRecord(Long userId, Long bookId, short round, ReadingStatus status,
                          Integer totalPagesOverride, LocalDate targetFinishDate) {
        this.userId = userId;
        this.bookId = bookId;
        this.round = round == 0 ? 1 : round;
        this.status = status == null ? ReadingStatus.WANT_TO_READ : status;
        this.totalPagesOverride = totalPagesOverride;
        this.targetFinishDate = targetFinishDate;
    }

    /** 세션 종료 시 진도 반영. 뒤로 읽기는 진도를 낮추지 않는다(§8.1). */
    public void applyProgress(int endPage, Instant readAt) {
        if (endPage > this.currentPage) {
            this.currentPage = endPage;
        }
        this.lastReadAt = readAt;
        if (this.status == ReadingStatus.WANT_TO_READ || this.status == ReadingStatus.PAUSED) {
            startReading(readAt);
        }
    }

    public void startReading(Instant at) {
        if (this.status.isClosed()) {
            return;
        }
        this.status = ReadingStatus.READING;
        if (this.startedAt == null) {
            this.startedAt = at;
        }
    }

    public void pause() {
        if (this.status.isClosed()) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 완독/하차한 책입니다.");
        }
        this.status = ReadingStatus.PAUSED;
    }

    public void finish(Instant at, Integer totalPages) {
        this.status = ReadingStatus.FINISHED;
        this.finishedAt = at;
        if (totalPages != null && totalPages > this.currentPage) {
            this.currentPage = totalPages;
        }
    }

    public void abandon(AbandonReason reason) {
        this.status = ReadingStatus.ABANDONED;
        this.abandonReason = reason == null ? AbandonReason.OTHER : reason;
    }

    public void reopen() {
        this.status = ReadingStatus.READING;
        this.finishedAt = null;
        this.abandonReason = null;
    }

    public void changeTargetDate(LocalDate targetFinishDate) {
        this.targetFinishDate = targetFinishDate;
    }

    public void overrideTotalPages(Integer totalPages) {
        this.totalPagesOverride = totalPages;
    }

    public void rate(Short rating) {
        this.rating = rating;
    }

    /** 사용자가 직접 진도를 수정하는 경우(세션 없이). */
    public void setCurrentPage(int page, Integer totalPages) {
        int max = totalPages == null ? Integer.MAX_VALUE : totalPages;
        if (page < 0 || page > max) {
            throw ApiException.of(ErrorCode.INVALID_PAGE_RANGE);
        }
        this.currentPage = page;
        this.lastReadAt = Instant.now();
    }

    public int effectiveTotalPages(Integer bookTotalPages) {
        if (totalPagesOverride != null && totalPagesOverride > 0) {
            return totalPagesOverride;
        }
        return bookTotalPages == null ? 0 : bookTotalPages;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
