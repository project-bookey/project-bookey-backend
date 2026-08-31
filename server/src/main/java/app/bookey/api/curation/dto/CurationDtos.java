package app.bookey.api.curation.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CurationDtos {
    private CurationDtos() {}

    /** 에디터 픽 조회용 — 도서 요약 정보를 포함한다. */
    public record EditorPickView(
            @NotNull Long id,
            @NotNull BookSummary book,
            int sortOrder,
            String note
    ) {}

    public record EditorPickCreateRequest(@NotNull Long bookId, int sortOrder, @Size(max = 200) String note) {}

    public record EditorPickUpdateRequest(int sortOrder, @Size(max = 200) String note) {}
}
