package app.bookey.admin;

import app.bookey.admin.dto.AdminContentDtos.AbuseReportRow;
import app.bookey.admin.dto.AdminContentDtos.AdminContentDetail;
import app.bookey.admin.dto.AdminContentDtos.AdminContentRow;
import app.bookey.admin.dto.AdminContentDtos.ContentActionRequest;
import app.bookey.admin.dto.AdminContentDtos.ModerationDetailView;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.ModerationSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 콘텐츠 검수와 신고 상세 (§F13 · §8.3). */
@Tag(name = "Admin Content", description = "콘텐츠 검수 · 신고 상세")
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminContentService contentService;
    private final AdminModerationService moderationService;

    @Operation(summary = "콘텐츠 목록 — 종류(type)는 POST·REVIEW·CLUB_POST·POST_COMMENT·REVIEW_COMMENT·BOOK_REMARK, 최근 순")
    @GetMapping("/contents")
    public PageResponse<AdminContentRow> contents(@AuthenticationPrincipal AuthAdmin admin,
                                                  @RequestParam ModerationSource type,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) Long bookId,
                                                  @RequestParam(required = false) Long clubId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "false") boolean reportedOnly,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return contentService.list(admin, type,
                new AdminContentService.Filter(userId, bookId, clubId, status, keyword, reportedOnly), page, size);
    }

    @Operation(summary = "콘텐츠 원문 — 열람 기록이 남는다. 비공개 독후감은 신고 처리 권한 필요")
    @GetMapping("/contents/{type}/{id}")
    public AdminContentDetail content(@AuthenticationPrincipal AuthAdmin admin,
                                      @PathVariable ModerationSource type,
                                      @PathVariable Long id) {
        return contentService.detail(admin, type, id);
    }

    @Operation(summary = "콘텐츠 조치 — 숨김·복구·삭제. 사유 필수, 열린 신고도 함께 처리한다")
    @PostMapping("/contents/{type}/{id}/actions")
    public ResponseEntity<Void> act(@AuthenticationPrincipal AuthAdmin admin,
                                    @PathVariable ModerationSource type,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ContentActionRequest request) {
        contentService.act(admin, type, id, request.action(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신고 상세 — 신고자·사유·원문·작성자 제재 이력. 열람 기록이 남는다")
    @GetMapping("/moderation/{ticketId}")
    public ModerationDetailView moderationDetail(@AuthenticationPrincipal AuthAdmin admin,
                                                 @PathVariable Long ticketId) {
        return moderationService.detail(admin, ticketId);
    }

    @Operation(summary = "한 회원이 신고한 내역 — 신고 남발 확인용")
    @GetMapping("/abuse-reports")
    public PageResponse<AbuseReportRow> reportsBy(@AuthenticationPrincipal AuthAdmin admin,
                                                  @RequestParam Long reporterId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return moderationService.reportsBy(admin, reporterId, page, size);
    }
}
