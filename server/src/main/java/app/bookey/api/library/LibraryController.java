package app.bookey.api.library;

import app.bookey.api.library.dto.LibraryDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.reading.ReadingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Library", description = "내 서재 · 독서 상태 · 목표")
@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    @Operation(summary = "서재에 책 추가")
    @PostMapping
    public ReadingRecordView add(@AuthenticationPrincipal AuthUser user,
                                 @Valid @RequestBody AddBookRequest request) {
        return libraryService.addBook(user.id(), request);
    }

    @Operation(summary = "서재 목록 — 상태별 필터")
    @GetMapping
    public PageResponse<ReadingRecordView> list(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) ReadingStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return libraryService.list(user.id(), status, PageRequest.of(page, size));
    }

    @Operation(summary = "서재 상태별 개수")
    @GetMapping("/summary")
    public LibrarySummary summary(@AuthenticationPrincipal AuthUser user) {
        return libraryService.summary(user.id());
    }

    @Operation(summary = "독서 기록 상세 — 진척도 포함")
    @GetMapping("/{recordId}")
    public ReadingRecordView detail(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long recordId) {
        return libraryService.detail(user.id(), recordId);
    }

    @Operation(summary = "목표(완독 예정일 · 총 페이지) 수정")
    @PatchMapping("/{recordId}/goal")
    public ReadingRecordView updateGoal(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable Long recordId,
                                        @Valid @RequestBody UpdateGoalRequest request) {
        return libraryService.updateGoal(user.id(), recordId, request);
    }

    @Operation(summary = "진도 직접 수정")
    @PatchMapping("/{recordId}/progress")
    public ReadingRecordView updateProgress(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long recordId,
                                            @Valid @RequestBody UpdateProgressRequest request) {
        return libraryService.updateProgress(user.id(), recordId, request.currentPage());
    }

    @Operation(summary = "일시정지 — 재촉 알림 중단")
    @PostMapping("/{recordId}/pause")
    public ReadingRecordView pause(@AuthenticationPrincipal AuthUser user, @PathVariable Long recordId) {
        return libraryService.pause(user.id(), recordId);
    }

    @Operation(summary = "다시 읽기 시작")
    @PostMapping("/{recordId}/resume")
    public ReadingRecordView resume(@AuthenticationPrincipal AuthUser user, @PathVariable Long recordId) {
        return libraryService.resume(user.id(), recordId);
    }

    @Operation(summary = "완독 처리")
    @PostMapping("/{recordId}/finish")
    public ReadingRecordView finish(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long recordId,
                                    @RequestBody(required = false) FinishRequest request) {
        return libraryService.finish(user.id(), recordId, request);
    }

    @Operation(summary = "하차 — 사유를 함께 기록한다")
    @PostMapping("/{recordId}/abandon")
    public ReadingRecordView abandon(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long recordId,
                                     @Valid @RequestBody AbandonRequest request) {
        return libraryService.abandon(user.id(), recordId, request);
    }

    @Operation(summary = "서재에서 삭제")
    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long recordId) {
        libraryService.delete(user.id(), recordId);
        return ResponseEntity.noContent().build();
    }
}
