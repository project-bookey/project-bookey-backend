package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowByCodeRequest;
import app.bookey.api.social.dto.SocialDtos.FollowCodeView;
import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Follow", description = "팔로우 — 16자리 코드(QR) 또는 상호 엽서로만 (§14.3)")
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "내 팔로우 코드 — QR 은 deepLink 로 그린다")
    @GetMapping("/my-code")
    public FollowCodeView myCode(@AuthenticationPrincipal AuthUser user) {
        return followService.myCode(user.id());
    }

    @Operation(summary = "팔로우 코드 회전 — 유출 시 무효화")
    @PostMapping("/my-code/rotate")
    public FollowCodeView rotate(@AuthenticationPrincipal AuthUser user) {
        return followService.rotate(user.id());
    }

    @Operation(summary = "코드로 팔로우 — 지인 전제, 즉시 맞팔로우")
    @PostMapping("/code")
    public FollowUserView followByCode(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody FollowByCodeRequest request) {
        return followService.followByCode(user.id(), request.code());
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
