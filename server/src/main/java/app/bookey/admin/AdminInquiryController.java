package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.InquiryAdminView;
import app.bookey.admin.dto.AdminDtos.InquiryAnswerRequest;
import app.bookey.admin.dto.AdminDtos.InquiryRow;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Inquiry", description = "고객문의 답변")
@RestController
@RequestMapping("/admin/v1/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryService inquiryService;

    @Operation(summary = "고객문의 목록 — 답변 대기만 보면 오래 기다린 순, 그 밖에는 최신순")
    @GetMapping
    public PageResponse<InquiryRow> list(@RequestParam(required = false) InquiryStatus status,
                                         @RequestParam(required = false) InquiryCategory category,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return inquiryService.list(status, category, page, size);
    }

    @Operation(summary = "고객문의 상세 — 열람 로그가 남는다")
    @GetMapping("/{inquiryId}")
    public InquiryAdminView detail(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long inquiryId) {
        return inquiryService.detail(admin, inquiryId);
    }

    @Operation(summary = "답변 등록 — 사용자에게 알림이 간다. 이미 답한 문의면 409")
    @PostMapping("/{inquiryId}/answer")
    public InquiryAdminView answer(@AuthenticationPrincipal AuthAdmin admin,
                                   @PathVariable Long inquiryId,
                                   @Valid @RequestBody InquiryAnswerRequest request) {
        return inquiryService.answer(admin, inquiryId, request.answer());
    }

    @Operation(summary = "답변 수정 — 알림은 다시 가지 않는다. 아직 답하지 않은 문의면 409")
    @PutMapping("/{inquiryId}/answer")
    public InquiryAdminView editAnswer(@AuthenticationPrincipal AuthAdmin admin,
                                       @PathVariable Long inquiryId,
                                       @Valid @RequestBody InquiryAnswerRequest request) {
        return inquiryService.editAnswer(admin, inquiryId, request.answer());
    }
}
