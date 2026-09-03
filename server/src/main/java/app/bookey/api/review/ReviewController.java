package app.bookey.api.review;

import app.bookey.api.club.dto.ClubDtos.ReportRequest;
import app.bookey.api.review.dto.ReviewDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "검증 리뷰 — 읽은 기록이 뒷받침된 리뷰")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "예상 검증 등급 — 리뷰 쓰기 전 확인")
    @GetMapping("/reviews/preview")
    public VerificationPreview preview(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam Long readingRecordId) {
        return reviewService.preview(user.id(), readingRecordId);
    }

    @Operation(summary = "리뷰 작성 — 작성 시점 검증 등급이 스냅샷으로 고정된다")
    @PostMapping("/reviews")
    public ReviewView create(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(user.id(), request);
    }

    @Operation(summary = "리뷰 수정 — 등급은 재산정하지 않는다")
    @PatchMapping("/reviews/{reviewId}")
    public ReviewView update(@AuthenticationPrincipal AuthUser user,
                             @PathVariable Long reviewId,
                             @Valid @RequestBody UpdateReviewRequest request) {
        return reviewService.update(user.id(), reviewId, request);
    }

    @Operation(summary = "리뷰 삭제")
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long reviewId) {
        reviewService.delete(user.id(), reviewId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리뷰 단건 조회")
    @GetMapping("/reviews/{reviewId}")
    public ReviewView detail(@PathVariable Long reviewId) {
        return reviewService.detail(reviewId);
    }

    @Operation(summary = "도서별 리뷰 목록 — 완독 검증 > 부분 검증 > 미검증 순")
    @GetMapping("/books/{bookId}/reviews")
    public PageResponse<ReviewView> listByBook(@PathVariable Long bookId,
                                               @RequestParam(defaultValue = "false") boolean verifiedOnly,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return reviewService.listByBook(bookId, verifiedOnly, PageRequest.of(page, size));
    }

    @Operation(summary = "내가 쓴 리뷰")
    @GetMapping("/reviews/me")
    public PageResponse<ReviewView> listMine(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return reviewService.listMine(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "도움됨 토글")
    @PostMapping("/reviews/{reviewId}/helpful")
    public ResponseEntity<Void> helpful(@PathVariable Long reviewId,
                                        @RequestParam(defaultValue = "true") boolean helpful) {
        reviewService.toggleHelpful(reviewId, helpful);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리뷰 신고")
    @PostMapping("/reviews/{reviewId}/reports")
    public ResponseEntity<Void> report(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long reviewId,
                                       @Valid @RequestBody ReportRequest request) {
        reviewService.report(user.id(), reviewId, request.reason(), request.detail());
        return ResponseEntity.noContent().build();
    }
}
