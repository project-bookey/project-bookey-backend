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
}
