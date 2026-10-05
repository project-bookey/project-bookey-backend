package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.BlockedUserView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Block", description = "차단 — 엽서·채팅을 막는다(한 방향, 상대에게 알리지 않음)")
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @Operation(summary = "차단 — 이미 막았으면 그대로 성공. 그 사람과의 엽서·채팅방이 내 목록에서 빠진다")
    @PostMapping("/{userId}")
    public BlockedUserView block(@AuthenticationPrincipal AuthUser user, @PathVariable Long userId) {
        return blockService.block(user.id(), userId);
    }

    @Operation(summary = "차단 풀기 — 막지 않았어도 그대로 성공")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal AuthUser user, @PathVariable Long userId) {
        blockService.unblock(user.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내가 차단한 사람 — 최근에 막은 순")
    @GetMapping
    public PageResponse<BlockedUserView> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return blockService.list(user.id(), PageRequest.of(page, size));
    }
}
