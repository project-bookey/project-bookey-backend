package app.bookey.api.faq;

import app.bookey.api.faq.dto.FaqDtos.FaqAdminView;
import app.bookey.api.faq.dto.FaqDtos.FaqOrderRequest;
import app.bookey.api.faq.dto.FaqDtos.FaqUpsertRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin FAQ", description = "FAQ 관리")
@RestController
@RequestMapping("/admin/v1/faqs")
@RequiredArgsConstructor
public class FaqAdminController {

    private final FaqService faqService;

    /** 보기는 모든 관리자에게 연다 — 더하고 고치는 것만 {@code canHandleSupport} 로 막는다. */
    @Operation(summary = "FAQ 전체 목록 — 숨긴 것 포함, 정렬 순")
    @GetMapping
    public List<FaqAdminView> list() {
        return faqService.adminList();
    }

    @Operation(summary = "FAQ 추가 — 맨 뒤에 붙는다")
    @PostMapping
    public FaqAdminView create(@AuthenticationPrincipal AuthAdmin admin,
                               @Valid @RequestBody FaqUpsertRequest request) {
        requireSupport(admin);
        return faqService.create(request);
    }

    /** 리터럴 {@code order} 는 {@code /{id}} 보다 먼저 매칭된다. */
    @Operation(summary = "FAQ 순서 바꾸기 — 전체 id 를 원하는 순서대로")
    @PutMapping("/order")
    public List<FaqAdminView> reorder(@AuthenticationPrincipal AuthAdmin admin,
                                      @Valid @RequestBody FaqOrderRequest request) {
        requireSupport(admin);
        return faqService.reorder(request.ids());
    }

    @Operation(summary = "FAQ 수정 · 숨기기 — 전체 필드 교체")
    @PutMapping("/{id}")
    public FaqAdminView update(@AuthenticationPrincipal AuthAdmin admin,
                               @PathVariable Long id,
                               @Valid @RequestBody FaqUpsertRequest request) {
        requireSupport(admin);
        return faqService.update(id, request);
    }

    @Operation(summary = "FAQ 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireSupport(admin);
        faqService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireSupport(AuthAdmin admin) {
        if (!admin.role().canHandleSupport()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
