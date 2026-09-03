package app.bookey.api.quote.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class QuoteDtos {

    private QuoteDtos() {}

    public record CreateBookQuoteRequest(@NotNull Long bookId, Long readingRecordId,
            @NotBlank @Size(max = 500) String content, @Min(1) Integer page) {}

    public record BookQuoteView(@NotNull Long id, @NotNull Long bookId, @NotNull String bookTitle,
            String bookCoverUrl, Integer page, @NotNull String content,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            long agreeCount, boolean agreedByMe, boolean mine, long commentCount,
            @NotNull Instant createdAt) {}

    /** 나도 그럼(agree) 토글 결과. */
    public record QuoteAgreeView(boolean agreed, long agreeCount) {}

    /** parentId 가 있으면 그 댓글의 답글이 된다. 답글에는 답글을 달 수 없다(1단계). */
    public record CreateQuoteCommentRequest(@NotBlank @Size(max = 300) String body, Long parentId) {}

    /** 밑줄에 덧붙인 말(댓글). 탈퇴한 작성자는 "알 수 없음". replyCount 는 답글 행에서 항상 0. */
    public record QuoteCommentView(@NotNull Long id, @NotNull Long quoteId, Long parentId,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull String body, boolean mine, long replyCount, @NotNull Instant createdAt) {}
}
