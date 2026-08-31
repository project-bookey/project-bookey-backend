package app.bookey.api.book.dto;

import app.bookey.domain.book.Book;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public final class BookDtos {

    private BookDtos() {}

    public record BookSummary(
            Long id,
            String isbn13,
            String title,
            String author,
            String publisher,
            LocalDate publishedAt,
            Integer totalPages,
            String coverUrl,
            String category,
            String source,
            /** 총 페이지 수가 없으면 클라이언트가 직접 입력을 유도해야 한다(§F1). */
            boolean needsPageInput
    ) {
        public static BookSummary from(Book book) {
            return new BookSummary(
                    book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthor(),
                    book.getPublisher(), book.getPublishedAt(), book.getTotalPages(),
                    book.getCoverUrl(), book.getCategory(), book.getSource().name(),
                    !book.hasTotalPages());
        }
    }

    public record BookDetail(
            BookSummary book,
            String description,
            RatingSummary verifiedRating,
            RatingSummary overallRating,
            long verifiedReviewCount
    ) {}

    public record RatingSummary(Double average, long count) {
        public static RatingSummary empty() {
            return new RatingSummary(null, 0);
        }
    }

    public record ManualBookRequest(
            @NotBlank @Size(max = 500) String title,
            @Size(max = 500) String author,
            @Size(max = 255) String publisher,
            @NotNull @Min(1) @Max(20000) Integer totalPages,
            @Size(max = 13) String isbn13,
            @Size(max = 255) String category,
            String coverUrl,
            LocalDate publishedAt
    ) {}

    public record PageSuggestionRequest(@NotNull @Min(1) @Max(20000) Integer totalPages) {}

    public record PageSuggestionResponse(Integer appliedTotalPages, int votes, boolean applied) {}
}
