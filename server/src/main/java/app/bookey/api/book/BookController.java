package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.*;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Book", description = "도서 검색 · 상세 · 수동 등록")
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Validated
public class BookController {

    private final BookService bookService;

    @Operation(summary = "도서 검색 (캐시 → 카카오 → 알라딘 보강 → 구글 폴백)")
    @GetMapping
    public List<BookSummary> search(@RequestParam @NotBlank String keyword,
                                    @RequestParam(defaultValue = "20") int size) {
        return bookService.search(keyword, size);
    }

    @Operation(summary = "ISBN 조회 — 바코드 스캔 결과 처리")
    @GetMapping("/isbn/{isbn13}")
    public BookSummary findByIsbn(@PathVariable String isbn13) {
        return bookService.findByIsbn(isbn13);
    }

    @Operation(summary = "도서 상세 — 검증 평점과 전체 평점을 분리해 제공")
    @GetMapping("/{bookId}")
    public BookDetail detail(@PathVariable Long bookId) {
        return bookService.detail(bookId);
    }

    @Operation(summary = "도서 수동 등록 — 독립출판·해외서 등")
    @PostMapping
    public BookSummary createManual(@Valid @RequestBody ManualBookRequest request) {
        return bookService.createManual(request);
    }

    @Operation(summary = "총 페이지 수 제보 — 동일 값 3표면 자동 채택")
    @PostMapping("/{bookId}/page-suggestions")
    public PageSuggestionResponse suggestPages(@AuthenticationPrincipal AuthUser user,
                                               @PathVariable Long bookId,
                                               @Valid @RequestBody PageSuggestionRequest request) {
        return bookService.suggestTotalPages(user.id(), bookId, request.totalPages());
    }
}
