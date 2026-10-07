package app.bookey.api.publicapi;

import app.bookey.api.book.BookService;
import app.bookey.api.book.Yes24CurationService;
import app.bookey.api.book.client.Yes24Client.CurationKind;
import app.bookey.api.book.dto.BookDtos.BookDetail;
import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.book.dto.BookDtos.PopularBookView;
import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.remark.RemarkService;
import app.bookey.api.remark.dto.RemarkDtos.RemarkView;
import app.bookey.api.review.ReviewService;
import app.bookey.api.review.dto.ReviewDtos.ReviewView;
import app.bookey.common.support.ClientKeys;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 비회원 공개 API — 책 (§F7 SEO 유입).
 * 공개 웹(www.bookey.site)이 로그인 없이 호출한다. 응답은 로그인 API 와 같은 DTO 라 열람자에 매인 값(liked 등)만 false 다.
 */
@Tag(name = "Public", description = "공개 웹(www.bookey.site) — 비회원 조회")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicBookController {

    private static final int MAX_LIST_SIZE = 50;
    private static final int MAX_RECOMMENDED_SIZE = 20;

    private final BookService bookService;
    private final ReviewService reviewService;
    private final PostService postService;
    private final RemarkService remarkService;
    private final Yes24CurationService yes24CurationService;

    @Operation(summary = "도서 검색 (비회원) — 외부 검색 API 를 타므로 같은 IP 는 분당 30회까지")
    @GetMapping("/books")
    public List<BookSummary> publicSearch(@RequestParam(defaultValue = "") String keyword,
                                          @RequestParam(defaultValue = "20") int size,
                                          HttpServletRequest request) {
        return bookService.searchPublic(keyword, size, ClientKeys.ipKey(request));
    }

    @Operation(summary = "인기 도서 (비회원) — 서재에 담긴 수 순")
    @GetMapping("/books/popular")
    public List<PopularBookView> publicPopular(@RequestParam(defaultValue = "20") int size) {
        return bookService.popular(PublicPaging.size(size, MAX_LIST_SIZE));
    }

    @Operation(summary = "추천 도서 (비회원) — 에디터 픽 → 기본 카테고리 → 최신 책 → YES24 베스트셀러")
    @GetMapping("/books/recommended")
    public List<BookSummary> publicRecommended(@RequestParam(defaultValue = "20") int size) {
        return bookService.recommended(null, PublicPaging.size(size, MAX_RECOMMENDED_SIZE));
    }

    @Operation(summary = "YES24 큐레이션 (비회원) — 베스트셀러·신간")
    @GetMapping("/books/curation/yes24")
    public List<BookSummary> publicYes24Curation(@RequestParam(defaultValue = "BESTSELLER") CurationKind kind,
                                                 @RequestParam(defaultValue = "20") int size) {
        return yes24CurationService.curation(kind, PublicPaging.size(size, MAX_LIST_SIZE));
    }

    @Operation(summary = "도서 공개 정보 — 평점·좋아요 수 포함, liked 는 늘 false")
    @GetMapping("/books/{bookId}")
    public BookDetail publicBook(@PathVariable Long bookId) {
        return bookService.detail(null, bookId);
    }

    @Operation(summary = "도서의 리뷰 (비회원) — 기본은 모든 리뷰, verifiedOnly=true 면 완독 확인된 리뷰만")
    @GetMapping("/books/{bookId}/reviews")
    public PageResponse<ReviewView> publicReviews(@PathVariable Long bookId,
                                                  @RequestParam(defaultValue = "false") boolean verifiedOnly,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return reviewService.listByBook(bookId, verifiedOnly, PublicPaging.of(page, size));
    }

    @Operation(summary = "도서에 달린 공개 독후감 (비회원)")
    @GetMapping("/books/{bookId}/posts")
    public PageResponse<PostView> publicBookPosts(@PathVariable Long bookId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return postService.listPublicByBook(bookId, PublicPaging.of(page, size));
    }

    @Operation(summary = "도서의 한 줄평 (비회원) — 최근에 쓴 순")
    @GetMapping("/books/{bookId}/remarks")
    public List<RemarkView> publicRemarks(@PathVariable Long bookId,
                                          @RequestParam(defaultValue = "20") int size) {
        return remarkService.listByBook(bookId, PublicPaging.size(size, MAX_LIST_SIZE));
    }

    @Operation(summary = "리뷰 한 건 (비회원)")
    @GetMapping("/reviews/{reviewId}")
    public ReviewView publicReview(@PathVariable Long reviewId) {
        return reviewService.detail(reviewId);
    }
}
