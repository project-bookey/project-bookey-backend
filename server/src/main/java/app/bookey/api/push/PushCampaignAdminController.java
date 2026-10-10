package app.bookey.api.push;

import app.bookey.admin.dto.AdminCsDtos.AdminReasonRequest;
import app.bookey.api.push.dto.PushCampaignDtos.*;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.push.PushCampaignKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 전체 푸시 (SUPER_ADMIN). */
@Tag(name = "Admin Push", description = "전체 푸시(캠페인) — 공지 · 광고")
@RestController
@RequestMapping("/admin/v1/push-campaigns")
@RequiredArgsConstructor
public class PushCampaignAdminController {

    private final PushCampaignService campaignService;

    @Operation(summary = "캠페인 목록 — 최근 순")
    @GetMapping
    public PageResponse<PushCampaignRow> list(@AuthenticationPrincipal AuthAdmin admin,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return campaignService.list(admin, page, size);
    }

    @Operation(summary = "대상자 수 미리보기 — 광고는 수신 동의자만")
    @GetMapping("/audience")
    public PushAudienceView audience(@AuthenticationPrincipal AuthAdmin admin, @RequestParam PushCampaignKind kind) {
        return campaignService.audience(admin, kind);
    }

    @Operation(summary = "캠페인 상세 — 발송·대기·열람 수")
    @GetMapping("/{id}")
    public PushCampaignView detail(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        return campaignService.detail(admin, id);
    }

    @Operation(summary = "캠페인 만들기 — scheduledAt 을 비우면 1분 안에 보내기 시작한다")
    @PostMapping
    public PushCampaignRow create(@AuthenticationPrincipal AuthAdmin admin,
                                  @Valid @RequestBody PushCampaignRequest request) {
        return campaignService.create(admin, request);
    }

    @Operation(summary = "예약 캠페인 고치기 — 보내기 시작하면 고칠 수 없다")
    @PutMapping("/{id}")
    public PushCampaignRow update(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id,
                                  @Valid @RequestBody PushCampaignRequest request) {
        return campaignService.update(admin, id, request);
    }

    @Operation(summary = "캠페인 취소 — 아직 안 나간 알림은 지운다")
    @PostMapping("/{id}/cancel")
    public PushCampaignRow cancel(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id,
                                  @Valid @RequestBody AdminReasonRequest request) {
        return campaignService.cancel(admin, id, request.reason());
    }

    @Operation(summary = "테스트 발송 — 지정한 회원(최대 5명)에게 [테스트] 를 붙여 바로 보낸다")
    @PostMapping("/test")
    public PushTestResult test(@AuthenticationPrincipal AuthAdmin admin,
                               @Valid @RequestBody PushTestRequest request) {
        return new PushTestResult(campaignService.test(admin, request));
    }
}
