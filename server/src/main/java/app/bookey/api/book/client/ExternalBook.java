package app.bookey.api.book.client;

import java.time.LocalDate;

/** 외부 API 응답을 정규화한 도서 정보. */
public record ExternalBook(
        String isbn13,
        String title,
        String subtitle,
        String author,
        String translator,
        String publisher,
        LocalDate publishedAt,
        Integer totalPages,
        String coverUrl,
        String category,
        String description
) {}
