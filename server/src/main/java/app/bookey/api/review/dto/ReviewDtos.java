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
            /** 답글 포함 전체 댓글 수. */
            long commentCount,
            @NotNull Instant createdAt
    ) {}

    /** parentId 가 있으면 그 댓글의 답글이 된다. 답글에는 답글을 달 수 없다(1단계). */
    public record CreateReviewCommentRequest(@NotBlank @Size(max = 300) String body, Long parentId) {}

    /** 리뷰에 덧붙인 말(댓글). 탈퇴한 작성자는 "알 수 없음". replyCount 는 답글 행에서 항상 0. */
    public record ReviewCommentView(@NotNull Long id, @NotNull Long reviewId, Long parentId,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull String body, boolean mine, long replyCount, @NotNull Instant createdAt) {}

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
