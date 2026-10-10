package app.bookey.api.publicapi;

import app.bookey.api.plaza.PlazaItemType;
import app.bookey.api.plaza.PlazaService;
import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.FeedSort;
import app.bookey.api.post.dto.PostDtos.PostView;
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

/**
 * 비회원 공개 API — 독후감·광장 (§F7 SEO 유입).
 * 공개 웹(www.bookey.site)이 로그인 없이 호출한다. likedByMe·mine 은 늘 false 다.
 */
@Tag(name = "Public", description = "공개 웹(www.bookey.site) — 비회원 조회")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicPostController {

    private final PostService postService;
    private final PlazaService plazaService;

    @Operation(summary = "광장 독후감 피드 (비회원) — HOT(좋아요·시간 감쇠) 또는 NEW(최신순)")
    @GetMapping("/posts/feed")
    public PageResponse<PostView> publicFeed(@RequestParam(defaultValue = "HOT") FeedSort sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return postService.feed(null, sort, PublicPaging.of(page, size));
    }

    @Operation(summary = "독후감 한 건 (비회원) — PUBLIC·LINK 글만. 같은 IP 는 1시간에 한 번만 조회수를 올리고, 봇은 세지 않는다")
    @GetMapping("/posts/{postId}")
    public PostView publicPost(@PathVariable Long postId, HttpServletRequest request) {
        return postService.getPublic(postId, ClientKeys.ipKey(request),
                ClientKeys.isBot(request.getHeader("User-Agent")));
    }

    @Operation(summary = "광장 완독 자랑 피드 (비회원)")
    @GetMapping("/plaza/feed")
    public PageResponse<PlazaItemView> publicPlazaFeed(@RequestParam(defaultValue = "FINISH") PlazaItemType type,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return plazaService.feed(type, PublicPaging.of(page, size));
    }
}
