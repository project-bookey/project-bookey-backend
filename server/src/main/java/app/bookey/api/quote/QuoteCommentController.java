package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.CreateQuoteCommentRequest;
import app.bookey.api.quote.dto.QuoteDtos.QuoteCommentView;
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

@Tag(name = "QuoteComment", description = "밑줄에 덧붙인 말(댓글) — 목록 · 답글 · 작성 · 삭제")
@RestController
@RequestMapping("/api/v1/quotes/{quoteId}/comments")
@RequiredArgsConstructor
public class QuoteCommentController {

    /** 답글은 한 번에 다 펼치는 화면이라 상한만 둔다. */
    private static final int MAX_REPLY_PAGE_SIZE = 100;

    private final QuoteCommentService commentService;

    @Operation(summary = "댓글 목록 — 최상위만, 오래된 순")
    @GetMapping
    public PageResponse<QuoteCommentView> list(@AuthenticationPrincipal AuthUser user,
                                               @PathVariable Long quoteId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "30") int size) {
        return commentService.list(user.id(), quoteId, PageRequest.of(page, size));
    }

    @Operation(summary = "답글 목록 — 오래된 순")
    @GetMapping("/{commentId}/replies")
    public PageResponse<QuoteCommentView> replies(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable Long quoteId,
                                                  @PathVariable Long commentId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return commentService.replies(user.id(), quoteId, commentId,
                PageRequest.of(page, Math.min(size, MAX_REPLY_PAGE_SIZE)));
    }

    @Operation(summary = "댓글 작성 — parentId 를 주면 답글(1단계)")
    @PostMapping
    public QuoteCommentView create(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long quoteId,
                                   @Valid @RequestBody CreateQuoteCommentRequest request) {
        return commentService.create(user.id(), quoteId, request);
    }

    @Operation(summary = "댓글 삭제 — 본인만")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long quoteId,
                                       @PathVariable Long commentId) {
        commentService.delete(user.id(), quoteId, commentId);
        return ResponseEntity.noContent().build();
    }
}
