package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.*;
import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.quote.QuoteService;
import app.bookey.api.quote.dto.QuoteDtos.BookQuoteView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    private final QuoteService quoteService;
    private final PostService postService;

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
    public BookDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookId) {
        return bookService.detail(user.id(), bookId);
    }

    @Operation(summary = "좋아요 토글")
    @PostMapping("/{bookId}/like")
    public BookLikeView like(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookId) {
        return bookService.toggleLike(user.id(), bookId);
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

    @Operation(summary = "인기 도서 — 서재에 담긴 수 순")
    @GetMapping("/popular")
    public List<PopularBookView> popular(@RequestParam(defaultValue = "20") int size) {
        return bookService.popular(size);
    }

    @Operation(summary = "추천 도서 — 에디터 픽")
    @GetMapping("/recommended")
    public List<BookSummary> recommended(@RequestParam(defaultValue = "20") int size) {
        return bookService.recommended(size);
    }

    @Operation(summary = "책별 오려둔 문장 목록 — 최신순")
    @GetMapping("/{bookId}/quotes")
    public PageResponse<BookQuoteView> quotes(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long bookId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return quoteService.byBook(user.id(), bookId, PageRequest.of(page, size));
    }

    @Operation(summary = "책별 공개 독후감 목록 — 최신순")
    @GetMapping("/{bookId}/posts")
    public PageResponse<PostView> posts(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable Long bookId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "5") int size) {
        return postService.listByBook(user.id(), bookId, PageRequest.of(page, size));
    }
}
