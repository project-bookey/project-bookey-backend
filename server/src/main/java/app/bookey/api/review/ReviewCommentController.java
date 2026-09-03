package app.bookey.api.review;

import app.bookey.api.review.dto.ReviewDtos.CreateReviewCommentRequest;
import app.bookey.api.review.dto.ReviewDtos.ReviewCommentView;
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

@Tag(name = "ReviewComment", description = "리뷰 댓글 — 목록 · 답글 · 작성 · 삭제")
@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/comments")
@RequiredArgsConstructor
public class ReviewCommentController {

    /** 0·음수 size 가 500 으로 새지 않게 1~100 으로 묶는다. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewCommentService commentService;

    @Operation(summary = "댓글 목록 — 최상위만, 오래된 순")
    @GetMapping
    public PageResponse<ReviewCommentView> list(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable Long reviewId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "30") int size) {
        // 음수 page 가 500 으로 새지 않게 0 아래로는 묶는다.
        return commentService.list(user.id(), reviewId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @Operation(summary = "답글 목록 — 오래된 순")
    @GetMapping("/{commentId}/replies")
    public PageResponse<ReviewCommentView> replies(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable Long reviewId,
                                                   @PathVariable Long commentId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        // 음수 page 가 500 으로 새지 않게 0 아래로는 묶는다.
        return commentService.replies(user.id(), reviewId, commentId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @Operation(summary = "댓글 작성 — parentId 를 주면 답글(1단계)")
    @PostMapping
    public ReviewCommentView create(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long reviewId,
                                    @Valid @RequestBody CreateReviewCommentRequest request) {
        return commentService.create(user.id(), reviewId, request);
    }

    @Operation(summary = "댓글 삭제 — 본인만")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long reviewId,
                                       @PathVariable Long commentId) {
        commentService.delete(user.id(), reviewId, commentId);
        return ResponseEntity.noContent().build();
    }
}
