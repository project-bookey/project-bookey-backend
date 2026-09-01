package app.bookey.api.challenge.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.domain.challenge.ChallengeStatus;
import app.bookey.domain.challenge.ReadingChallenge;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ChallengeDtos {
    private ChallengeDtos() {}

    public record CreateChallengeRequest(@NotNull Long readingRecordId, @Min(600) int budgetSec) {}

    public record ChallengeProgressRequest(@Min(0) int currentPage) {}

    /** 챌린지 상태 — elapsed/remaining은 서버가 now 기준으로 계산해 내려준다. */
    public record ChallengeView(
            @NotNull Long id,
            @NotNull Long readingRecordId,
            BookSummary book,
            int budgetSec,
            int elapsedSec,
            int remainingSec,
            boolean running,
            @NotNull ChallengeStatus status,
            int currentPage,
            int totalPages,
            Instant completedAt
    ) {
        public static ChallengeView of(ReadingChallenge c, BookSummary book,
                                       int currentPage, int totalPages, Instant now) {
            return new ChallengeView(c.getId(), c.getReadingRecordId(), book,
                    c.getBudgetSec(), c.effectiveElapsedSec(now), c.remainingSec(now),
                    c.isRunning(), c.getStatus(), currentPage, totalPages, c.getCompletedAt());
        }
    }
}
