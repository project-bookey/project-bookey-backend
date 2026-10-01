package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.api.social.dto.SocialDtos.FollowingIdsView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Follow", description = "팔로우 — 버튼 한 번, 한 방향 (§14.3)")
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우 — 한 방향, 이미 팔로우 중이면 그대로 성공")
    @PostMapping("/{userId}")
    public FollowUserView follow(@AuthenticationPrincipal AuthUser user, @PathVariable Long userId) {
        return followService.follow(user.id(), userId);
    }

    @Operation(summary = "내가 팔로우하는 사람 id 전부 — 팔로우 버튼 상태 판정용")
    @GetMapping("/following-ids")
    public FollowingIdsView followingIds(@AuthenticationPrincipal AuthUser user) {
        return followService.followingIds(user.id());
    }

    @Operation(summary = "팔로워 목록")
    @GetMapping("/followers")
    public PageResponse<FollowUserView> followers(@AuthenticationPrincipal AuthUser user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return followService.followers(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "팔로잉 목록")
    @GetMapping("/following")
    public PageResponse<FollowUserView> following(@AuthenticationPrincipal AuthUser user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return followService.following(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "언팔로우 — 내 방향만 끊는다")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable Long userId) {
        followService.unfollow(user.id(), userId);
        return ResponseEntity.noContent().build();
    }
}
