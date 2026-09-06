package app.bookey.api.social;

import app.bookey.api.post.PostService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.social.dto.SocialDtos.LikerView;
import app.bookey.api.social.dto.SocialDtos.UserProfileView;
import app.bookey.api.social.dto.SocialDtos.VisitorView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "유저 마이페이지 · 방문 기록 · 좋아요 열람 (§14.2·14.3)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PostService postService;

    @Operation(summary = "유저 프로필 — 열람 시 방문 기록이 남는다 (방문 수는 전체 공개)")
    @GetMapping("/users/{userId}/profile")
    public UserProfileView profile(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long userId) {
        return profileService.profile(user.id(), userId);
    }

    @Operation(summary = "유저의 공개 독후감 — 피드에서 휘발된 글도 여기엔 축적")
    @GetMapping("/users/{userId}/posts")
    public PageResponse<PostView> posts(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable Long userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return postService.listPublicByUser(user.id(), userId, PageRequest.of(page, size));
    }

    @Operation(summary = "내 방문자 목록 — 구독 회원 전용 (숫자는 프로필에서 전체 공개)")
    @GetMapping("/me/visitors")
    public PageResponse<VisitorView> visitors(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return profileService.visitors(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "내 글에 좋아요 누른 사람 — 글 주인 + 구독 회원 전용")
    @GetMapping("/posts/{postId}/likers")
    public PageResponse<LikerView> likers(@AuthenticationPrincipal AuthUser user,
                                          @PathVariable Long postId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return profileService.likers(user.id(), postId, PageRequest.of(page, size));
    }
}
