package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.CreateQuoteCommentRequest;
import app.bookey.api.quote.dto.QuoteDtos.QuoteCommentView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.quote.BookQuoteRepository;
import app.bookey.domain.quote.QuoteComment;
import app.bookey.domain.quote.QuoteCommentRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 밑줄에 덧붙인 말(댓글) — 목록(오래된 순) · 작성 · 본인 삭제. */
@Service
@RequiredArgsConstructor
public class QuoteCommentService {

    /** 도배 방지 — 1분에 20건. */
    private static final int CREATE_RATE_LIMIT = 20;

    private final QuoteCommentRepository commentRepository;
    private final BookQuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    /** 댓글 목록 — 오래된 순. 입력창이 아래에 있으므로 새 댓글이 바로 위에 보인다. */
    @Transactional(readOnly = true)
    public PageResponse<QuoteCommentView> list(Long userId, Long quoteId, Pageable pageable) {
        requireQuote(quoteId);
        Page<QuoteComment> page = commentRepository.findAllByQuoteIdOrderByCreatedAtAscIdAsc(quoteId, pageable);
        List<QuoteComment> comments = page.getContent();
        List<QuoteCommentView> views = assembleViews(comments, userId, loadAuthors(comments));
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @Transactional
    public QuoteCommentView create(Long userId, Long quoteId, CreateQuoteCommentRequest request) {
        requireQuote(quoteId);
        rateLimiter.require("quote:comment:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        QuoteComment comment = commentRepository.save(QuoteComment.builder()
                .quoteId(quoteId)
                .userId(userId)
                .body(request.body().trim())
                .build());

        User author = userRepository.findById(userId).orElse(null);
        return assembleViews(List.of(comment), userId,
                author == null ? Map.of() : Map.of(userId, author)).get(0);
    }

    /** 본인 댓글만 지운다. 경로의 밑줄에 달리지 않은 댓글은 없는 것으로 본다. */
    @Transactional
    public void delete(Long userId, Long quoteId, Long commentId) {
        QuoteComment comment = commentRepository.findById(commentId)
                .filter(found -> found.belongsTo(quoteId))
                .orElseThrow(() -> ApiException.of(ErrorCode.QUOTE_COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    private void requireQuote(Long quoteId) {
        if (!quoteRepository.existsById(quoteId)) {
            throw ApiException.of(ErrorCode.QUOTE_NOT_FOUND);
        }
    }

    private Map<Long, User> loadAuthors(List<QuoteComment> comments) {
        List<Long> ids = comments.stream().map(QuoteComment::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 배치 맵으로 뷰를 조립한다(QuoteService.assembleViews 선례). 탈퇴한 작성자는 "알 수 없음". */
    static List<QuoteCommentView> assembleViews(List<QuoteComment> comments, Long viewerId,
                                                Map<Long, User> authors) {
        return comments.stream()
                .map(comment -> {
                    User author = authors.get(comment.getUserId());
                    return new QuoteCommentView(
                            comment.getId(),
                            comment.getQuoteId(),
                            comment.getUserId(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            author == null ? null : author.getAvatarUrl(),
                            comment.getBody(),
                            comment.isOwnedBy(viewerId),
                            comment.getCreatedAt() == null ? Instant.now() : comment.getCreatedAt());
                })
                .toList();
    }
}
