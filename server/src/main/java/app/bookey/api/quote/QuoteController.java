package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.*;
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

@Tag(name = "Quote", description = "오려둔 문장(밑줄) — 작성 · 삭제 · 목록 · 나도 그럼")
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @Operation(summary = "문장 오려두기")
    @PostMapping
    public BookQuoteView create(@AuthenticationPrincipal AuthUser user,
                                @Valid @RequestBody CreateBookQuoteRequest request) {
        return quoteService.create(user.id(), request);
    }

    @Operation(summary = "내 오려둔 문장 목록 — 최신순, bookId 로 책 하나만 추리고 q 로 문장·책 제목 검색")
    @GetMapping
    public PageResponse<BookQuoteView> myQuotes(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) Long bookId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return quoteService.mine(user.id(), bookId, q, PageRequest.of(page, size));
    }

    @Operation(summary = "문장 한 건 — 상세 진입용")
    @GetMapping("/{quoteId}")
    public BookQuoteView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long quoteId) {
        return quoteService.get(user.id(), quoteId);
    }

    @Operation(summary = "문장 삭제 — 본인만")
    @DeleteMapping("/{quoteId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long quoteId) {
        quoteService.delete(user.id(), quoteId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "나도 그럼 — agree 토글")
    @PostMapping("/{quoteId}/agree")
    public QuoteAgreeView agree(@AuthenticationPrincipal AuthUser user, @PathVariable Long quoteId) {
        return quoteService.toggleAgree(user.id(), quoteId);
    }
}
