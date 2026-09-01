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
            long agreeCount, boolean agreedByMe, boolean mine, @NotNull Instant createdAt) {}

    /** 나도 그럼(agree) 토글 결과. */
    public record QuoteAgreeView(boolean agreed, long agreeCount) {}
}
