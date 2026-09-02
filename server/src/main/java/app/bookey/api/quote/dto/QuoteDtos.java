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

    public record CreateQuoteCommentRequest(@NotBlank @Size(max = 300) String body) {}

    /** 밑줄에 덧붙인 말(댓글). 탈퇴한 작성자는 "알 수 없음". */
    public record QuoteCommentView(@NotNull Long id, @NotNull Long quoteId,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull String body, boolean mine, @NotNull Instant createdAt) {}
}
