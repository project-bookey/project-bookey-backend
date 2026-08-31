package app.bookey.api.publicapi;

import app.bookey.api.book.BookService;
import app.bookey.api.book.dto.BookDtos.BookDetail;
import app.bookey.api.post.PostService;
import app.bookey.api.post.PostService.PostView;
import app.bookey.api.review.ReviewService;
import app.bookey.api.review.dto.ReviewDtos.ReviewView;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 비회원 공개 API (§F7 SEO 유입).
 * Next.js 공개 웹이 SSR 로 호출한다. 인증 없이 읽기만 가능하다.
 */
@Tag(name = "Public", description = "공개 웹 — 독후감 · 검증 리뷰 (비회원)")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PostService postService;
    private final ReviewService reviewService;
    private final BookService bookService;

    @Operation(summary = "사용자 공개 블로그 — bookey.app/@{handle}")
    @GetMapping("/blogs/{handle}/posts")
    public PageResponse<PostView> blogPosts(@PathVariable String handle,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return postService.listPublicByHandle(handle, PageRequest.of(page, size));
    }

    @Operation(summary = "공개 독후감 상세")
    @GetMapping("/blogs/{handle}/posts/{slug}")
    public PostView blogPost(@PathVariable String handle, @PathVariable String slug) {
        return postService.readPublic(handle, slug);
    }

    @Operation(summary = "도서 공개 정보 — 검증 평점 포함")
    @GetMapping("/books/{bookId}")
    public BookDetail book(@PathVariable Long bookId) {
        return bookService.detail(bookId);
    }

    @Operation(summary = "도서의 검증 리뷰")
    @GetMapping("/books/{bookId}/reviews")
    public PageResponse<ReviewView> reviews(@PathVariable Long bookId,
                                            @RequestParam(defaultValue = "true") boolean verifiedOnly,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listByBook(bookId, verifiedOnly, PageRequest.of(page, size));
    }

    @Operation(summary = "도서에 달린 공개 독후감")
    @GetMapping("/books/{bookId}/posts")
    public PageResponse<PostView> bookPosts(@PathVariable Long bookId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return postService.listPublicByBook(bookId, PageRequest.of(page, size));
    }
}
