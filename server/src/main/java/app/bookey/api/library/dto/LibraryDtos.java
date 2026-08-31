package app.bookey.api.library.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.domain.reading.AbandonReason;
import app.bookey.domain.reading.ReadingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public final class LibraryDtos {

    private LibraryDtos() {}

    public record AddBookRequest(
            @NotNull Long bookId,
            ReadingStatus status,
            LocalDate targetFinishDate,
            @Min(1) @Max(20000) Integer totalPagesOverride,
            /** true 면 이미 완독한 책이라도 새 회차(재독)로 등록한다. */
            Boolean reread
    ) {}

    public record UpdateGoalRequest(
            LocalDate targetFinishDate,
            @Min(1) @Max(20000) Integer totalPagesOverride
    ) {}

    public record UpdateProgressRequest(@NotNull @Min(0) Integer currentPage) {}

    public record FinishRequest(@Min(1) @Max(5) Short rating) {}

    public record AbandonRequest(@NotNull AbandonReason reason) {}

    /** 진척도 (§F4). */
    public record ProgressView(
            int currentPage,
            int totalPages,
            Double completionRate,
            int remainingPages,
            Double requiredDailyPace,
            Double actualDailyPace,
            Double paceGap,
            LocalDate estimatedFinishDate,
            Long daysSinceLastRead,
            @NotNull String lagLevel,
            long totalDurationSec
    ) {}

    public record ReadingRecordView(
            @NotNull Long id,
            short round,
            @NotNull ReadingStatus status,
            BookSummary book,
            @NotNull ProgressView progress,
            LocalDate targetFinishDate,
            Instant startedAt,
            Instant finishedAt,
            Instant lastReadAt,
            Short rating,
            String abandonReason
    ) {}

    public record LibrarySummary(
            long reading,
            long wantToRead,
            long finished,
            long abandoned,
            long paused
    ) {}
}
