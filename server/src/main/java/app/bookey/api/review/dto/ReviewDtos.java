package app.bookey.api.review.dto;

import app.bookey.domain.review.VerificationLevel;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReviewDtos {

    private ReviewDtos() {}

    public record CreateReviewRequest(
            @NotNull Long readingRecordId,
            @Min(1) @Max(5) Short rating,
            @NotBlank @Size(max = 5000) String body,
            List<String> tags,
            Boolean hasSpoiler,
            /** 같은 내용으로 독후감도 함께 만들지 (§F7). */
            Boolean alsoCreatePost
    ) {}

    public record UpdateReviewRequest(
            @Min(1) @Max(5) Short rating,
            @Size(max = 5000) String body,
            List<String> tags,
            Boolean hasSpoiler
    ) {}

    public record ReviewView(
            @NotNull Long id,
            @NotNull Long bookId,
            @NotNull Long authorId,
            @NotNull String authorNickname,
            String authorHandle,
            Short rating,
            @NotNull String body,
            List<String> tags,
            boolean hasSpoiler,
            @NotNull VerificationLevel verificationLevel,
            Map<String, Object> verificationSnapshot,
            int helpfulCount,
            @NotNull Instant createdAt
    ) {}

    /** 리뷰 작성 전 미리 보여주는 예상 등급 — "지금 쓰면 어떤 배지를 받는지" */
    public record VerificationPreview(
            @NotNull VerificationLevel expectedLevel,
            double coverage,
            int timerSessionCount,
            long verifiedMinutes,
            long requiredMinutes,
            List<String> flags,
            boolean canRate
    ) {}
}
