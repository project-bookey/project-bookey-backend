package app.bookey.api.novel;

import app.bookey.api.auth.WriteBanGuarded;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/novels")
@RequiredArgsConstructor
public class NovelWritingAssistController {
    private final NovelWritingAssistService service;
    private final NovelStoryService stories;

    @Operation(summary = "현재 집필 차례의 원고 일부를 다듬기 — 저장하거나 발행하지 않는다")
    @PostMapping("/{id}/writing-assist") @WriteBanGuarded
    public NovelAiClient.Rewrite turn(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                      @Valid @RequestBody NovelWritingAssistService.Request request) {
        return service.turn(user.id(), id, request);
    }

    @Operation(summary = "내가 받은 열매의 원고 일부를 다듬기 — 저장하거나 발행하지 않는다")
    @PostMapping("/deliveries/{deliveryId}/writing-assist") @WriteBanGuarded
    public NovelAiClient.Rewrite branch(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId,
                                        @Valid @RequestBody NovelWritingAssistService.Request request) {
        return service.branch(user.id(), deliveryId, request);
    }

    @Operation(summary = "현재 회차의 이어쓰기·전개 추천·첫 문단 제안")
    @PostMapping("/{id}/story-assist") @WriteBanGuarded
    public NovelAiClient.Story story(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                     @Valid @RequestBody NovelStoryService.Request request) {
        return stories.turn(user.id(), id, request);
    }

    @Operation(summary = "배달받은 회차를 참고한 이어쓰기·전개 추천·첫 문단 제안")
    @PostMapping("/deliveries/{deliveryId}/story-assist") @WriteBanGuarded
    public NovelAiClient.Story branchStory(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId,
                                          @Valid @RequestBody NovelStoryService.Request request) {
        return stories.branch(user.id(), deliveryId, request);
    }
}
