package app.bookey.admin;

import app.bookey.admin.dto.AdminCatalogDtos.*;
import app.bookey.admin.dto.AdminCsDtos.AdminReasonRequest;
import app.bookey.admin.dto.AdminDtos.BookRow;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.club.ClubMemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 도서 등록·사용처·페이지 수 제안·병합, 모임 상세·멤버·강퇴. */
@Tag(name = "Admin Catalog", description = "도서 · 모임 관리")
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminCatalogService catalogService;

    @Operation(summary = "도서 직접 등록 — 외부 검색에 없는 책. ISBN 이 이미 있으면 409")
    @PostMapping("/books")
    public BookRow createBook(@AuthenticationPrincipal AuthAdmin admin,
                              @Valid @RequestBody AdminBookCreateRequest request) {
        return catalogService.createBook(admin, request);
    }

    @Operation(summary = "도서 상세 — 독서 기록·리뷰·독후감 등 이 책을 쓰는 곳의 수")
    @GetMapping("/books/{bookId}")
    public AdminBookView book(@PathVariable Long bookId) {
        return catalogService.book(bookId);
    }

    @Operation(summary = "페이지 수 제안이 모인 책 — onlyConflicts 면 지금 값과 최다 득표가 다른 책만")
    @GetMapping("/page-suggestions")
    public PageResponse<PageSuggestionRow> pageSuggestions(@RequestParam(defaultValue = "true") boolean onlyConflicts,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return catalogService.pageSuggestions(onlyConflicts, page, size);
    }

    @Operation(summary = "한 책의 페이지 수 제안 집계 — 많이 나온 순")
    @GetMapping("/books/{bookId}/page-suggestions")
    public List<PageSuggestionTally> tally(@PathVariable Long bookId) {
        return catalogService.tally(bookId);
    }

    @Operation(summary = "페이지 수 제안 비우기 — 사유 필수")
    @DeleteMapping("/books/{bookId}/page-suggestions")
    public RemovedCountView clearSuggestions(@AuthenticationPrincipal AuthAdmin admin,
                                             @PathVariable Long bookId,
                                             @RequestParam String reason) {
        return new RemovedCountView(catalogService.clearSuggestions(admin, bookId, reason));
    }

    @Operation(summary = "도서 병합 미리보기 (SUPER_ADMIN) — 겹침과 막는 이유")
    @GetMapping("/books/{bookId}/merge-preview")
    public BookMergePreview mergePreview(@AuthenticationPrincipal AuthAdmin admin,
                                         @PathVariable Long bookId,
                                         @RequestParam Long targetId) {
        return catalogService.mergePreview(admin, bookId, targetId);
    }

    @Operation(summary = "도서 병합 (SUPER_ADMIN) — 원본을 가리키던 모든 기록을 대상으로 옮기고 원본을 지운다. 되돌릴 수 없다")
    @PostMapping("/books/{bookId}/merge")
    public BookMergeResult merge(@AuthenticationPrincipal AuthAdmin admin,
                                 @PathVariable Long bookId,
                                 @Valid @RequestBody BookMergeRequest request) {
        return catalogService.merge(admin, bookId, request);
    }

    @Operation(summary = "모임 상세")
    @GetMapping("/clubs/{clubId}")
    public AdminClubView club(@PathVariable Long clubId) {
        return catalogService.club(clubId);
    }

    @Operation(summary = "모임 멤버 — 나간·내보내진 멤버까지. status 로 거른다")
    @GetMapping("/clubs/{clubId}/members")
    public List<AdminClubMemberRow> members(@PathVariable Long clubId,
                                            @RequestParam(required = false) ClubMemberStatus status) {
        return catalogService.members(clubId, status);
    }

    @Operation(summary = "모임 멤버 내보내기 — 사유 필수. 호스트는 먼저 넘긴 뒤에")
    @PostMapping("/clubs/{clubId}/members/{userId}/kick")
    public ResponseEntity<Void> kick(@AuthenticationPrincipal AuthAdmin admin,
                                     @PathVariable Long clubId,
                                     @PathVariable Long userId,
                                     @Valid @RequestBody AdminReasonRequest request) {
        catalogService.kick(admin, clubId, userId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
