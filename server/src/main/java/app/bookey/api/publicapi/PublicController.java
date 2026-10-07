package app.bookey.api.publicapi;

import app.bookey.api.book.BookService;
import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 비회원 공개 API — 블로그·온보딩 (§F7 SEO 유입).
 * 공개 웹(www.bookey.site, Next.js)과 앱 온보딩이 로그인 없이 호출한다.
 * 책·독후감·사용자 조회는 {@link PublicBookController}·{@link PublicPostController}·{@link PublicUserController}.
 */
@Tag(name = "Public", description = "공개 웹(www.bookey.site) — 비회원 조회")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PostService postService;
    private final BookService bookService;
    private final app.bookey.api.book.Yes24CurationService yes24CurationService;

    @Operation(summary = "사용자 공개 블로그 — @{handle} 의 공개 독후감")
    @GetMapping("/blogs/{handle}/posts")
    public PageResponse<PostView> blogPosts(@PathVariable String handle,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return postService.listPublicByHandle(handle, PublicPaging.of(page, size));
    }

    @Operation(summary = "공개 독후감 상세 — @{handle}/{slug}")
    @GetMapping("/blogs/{handle}/posts/{slug}")
    public PostView blogPost(@PathVariable String handle, @PathVariable String slug) {
        return postService.readPublic(handle, slug);
    }

    @Operation(summary = "온보딩 책 고르기 — 표지 있는 책, 카테고리 부분 일치 (비회원)")
    @GetMapping("/onboarding/books")
    public java.util.List<app.bookey.api.book.dto.BookDtos.BookSummary> onboardingBooks(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "30") int size) {
        int capped = Math.min(size, 60);
        java.util.List<app.bookey.api.book.dto.BookDtos.BookSummary> books =
                new java.util.ArrayList<>(bookService.onboardingPicks(category, capped));
        // 내부 책이 모자라면 YES24 베스트셀러로 채운다 — 첫 가입자도 고를 책이 있게.
        if (books.size() < capped) {
            java.util.Set<Long> seen = books.stream()
                    .map(app.bookey.api.book.dto.BookDtos.BookSummary::id)
                    .collect(java.util.stream.Collectors.toSet());
            yes24CurationService.curation(
                            app.bookey.api.book.client.Yes24Client.CurationKind.BESTSELLER, capped)
                    .stream()
                    .filter(b -> seen.add(b.id()))
                    .limit(capped - books.size())
                    .forEach(books::add);
        }
        return books;
    }

    @Operation(summary = "온보딩 선호 카테고리 — 실제 책 메타 기반 (비회원)")
    @GetMapping("/onboarding/categories")
    public java.util.List<String> onboardingCategories() {
        return bookService.onboardingCategories();
    }
}
