package app.bookey.api.inquiry;

import app.bookey.api.inquiry.dto.InquiryDtos.CreateInquiryRequest;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryCategoryView;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryImageView;
import app.bookey.api.inquiry.dto.InquiryDtos.InquirySummaryView;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Inquiry", description = "고객문의(1:1)")
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final InquiryImageService inquiryImageService;

    /** 리터럴 {@code categories}·{@code images} 는 {@code /{inquiryId}} 보다 먼저 매칭된다. */
    @Operation(summary = "문의 유형 — 앱 칩 순서, 맨 앞이 기본값")
    @GetMapping("/categories")
    public List<InquiryCategoryView> categories() {
        return inquiryService.categories();
    }

    @Operation(summary = "문의 사진 업로드 — 문의에 붙이기 전 임시 저장, 24시간 안에 안 붙이면 삭제")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InquiryImageView upload(@AuthenticationPrincipal AuthUser user,
                                   @RequestPart("file") MultipartFile file) {
        return inquiryImageService.upload(user.id(), file);
    }

    @Operation(summary = "문의 남기기")
    @PostMapping
    public InquiryView create(@AuthenticationPrincipal AuthUser user,
                              @Valid @RequestBody CreateInquiryRequest request) {
        return inquiryService.create(user.id(), request);
    }

    @Operation(summary = "내 문의 목록 — 최신순")
    @GetMapping
    public PageResponse<InquirySummaryView> listMine(@AuthenticationPrincipal AuthUser user,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return inquiryService.myList(user.id(), page, size);
    }

    @Operation(summary = "문의 한 건과 답변 — 내 문의만")
    @GetMapping("/{inquiryId}")
    public InquiryView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long inquiryId) {
        return inquiryService.detail(user.id(), inquiryId);
    }

    @Operation(summary = "문의 삭제 — 답변 전후 상관없이 지운다")
    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long inquiryId) {
        inquiryService.delete(user.id(), inquiryId);
        return ResponseEntity.noContent().build();
    }
}
