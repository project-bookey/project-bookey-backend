package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.PostcardView;
import app.bookey.api.social.dto.SocialDtos.ReplyPostcardRequest;
import app.bookey.api.social.dto.SocialDtos.SendPostcardRequest;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Postcard", description = "엽서 — 16글자 연결 요청, 답장하면 맞팔로우 (§14.2)")
@RestController
@RequestMapping("/api/v1/postcards")
@RequiredArgsConstructor
public class PostcardController {

    private final PostcardService postcardService;

    @Operation(summary = "엽서 보내기 — 무료 일 5장(KST 자정 리셋) → 보유 엽서. 우표 동봉 가능")
    @PostMapping
    public PostcardView send(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody SendPostcardRequest request) {
        return postcardService.send(user.id(), request);
    }

    @Operation(summary = "받은 엽서")
    @GetMapping("/inbox")
    public PageResponse<PostcardView> inbox(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return postcardService.inbox(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "보낸 엽서")
    @GetMapping("/sent")
    public PageResponse<PostcardView> sent(@AuthenticationPrincipal AuthUser user,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return postcardService.sent(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "답장 — 우표 1개 소모(동봉 엽서는 무료). 성립하면 자동 맞팔로우")
    @PostMapping("/{postcardId}/reply")
    public PostcardView reply(@AuthenticationPrincipal AuthUser user,
                              @PathVariable Long postcardId,
                              @Valid @RequestBody ReplyPostcardRequest request) {
        return postcardService.reply(user.id(), postcardId, request);
    }
}
