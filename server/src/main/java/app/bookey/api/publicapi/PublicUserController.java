package app.bookey.api.publicapi;

import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.social.ProfileService;
import app.bookey.api.social.dto.SocialDtos.UserProfileView;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비회원 공개 API — 사용자 (§F7 SEO 유입).
 * 공개 웹(www.bookey.site)이 로그인 없이 호출한다. 방문을 남기지 않고, 열람자에 매인 값은 모두 false 다.
 */
@Tag(name = "Public", description = "공개 웹(www.bookey.site) — 비회원 조회")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicUserController {

    private final ProfileService profileService;
    private final PostService postService;

    @Operation(summary = "공개 프로필 (비회원) — 닉네임·사진·숫자만, 방문 기록은 남기지 않는다")
    @GetMapping("/users/{userId}/profile")
    public UserProfileView publicProfile(@PathVariable Long userId) {
        return profileService.publicProfile(userId);
    }

    @Operation(summary = "사용자의 공개 독후감 (비회원) — 없는·탈퇴한 사람은 404")
    @GetMapping("/users/{userId}/posts")
    public PageResponse<PostView> publicUserPosts(@PathVariable Long userId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        profileService.requireUser(userId);
        return postService.listPublicByUser(null, userId, PublicPaging.of(page, size));
    }
}
