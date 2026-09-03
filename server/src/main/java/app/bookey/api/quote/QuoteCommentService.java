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
import app.bookey.domain.quote.QuoteCommentRepository.ReplyCount;
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

/** 밑줄에 덧붙인 말(댓글) — 목록(최상위, 오래된 순) · 답글(1단계) · 작성 · 본인 삭제. */
@Service
@RequiredArgsConstructor
public class QuoteCommentService {

    /** 도배 방지 — 1분에 20건. */
    private static final int CREATE_RATE_LIMIT = 20;

    private final QuoteCommentRepository commentRepository;
    private final BookQuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    /**
     * 댓글 목록 — 최상위만, 오래된 순. 입력창이 아래에 있으므로 새 댓글이 바로 위에 보인다.
     * 답글은 접어두고 replyCount 만 실어 보낸다.
     */
    @Transactional(readOnly = true)
    public PageResponse<QuoteCommentView> list(Long userId, Long quoteId, Pageable pageable) {
        requireQuote(quoteId);
        Page<QuoteComment> page =
                commentRepository.findAllByQuoteIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(quoteId, pageable);
        List<QuoteComment> comments = page.getContent();
        List<QuoteCommentView> views =
                assembleViews(comments, userId, loadAuthors(comments), loadReplyCounts(comments));
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** 한 댓글의 답글 목록 — 오래된 순. 답글에는 답글이 없으므로 replyCount 는 모두 0이다. */
    @Transactional(readOnly = true)
    public PageResponse<QuoteCommentView> replies(Long userId, Long quoteId, Long commentId, Pageable pageable) {
        requireQuote(quoteId);
        requireComment(quoteId, commentId);
        Page<QuoteComment> page = commentRepository.findAllByParentIdOrderByCreatedAtAscIdAsc(commentId, pageable);
        List<QuoteComment> replies = page.getContent();
        List<QuoteCommentView> views = assembleViews(replies, userId, loadAuthors(replies), Map.of());
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** parentId 를 주면 그 댓글의 답글이 된다. 답글에는 답글을 달 수 없다(1단계). */
    @Transactional
    public QuoteCommentView create(Long userId, Long quoteId, CreateQuoteCommentRequest request) {
        requireQuote(quoteId);
        rateLimiter.require("quote:comment:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        Long parentId = request.parentId() == null
                ? null
                : requireRepliable(requireComment(quoteId, request.parentId())).getId();

        QuoteComment comment = commentRepository.save(QuoteComment.builder()
                .quoteId(quoteId)
                .userId(userId)
                .parentId(parentId)
                .body(request.body().trim())
                .build());

        User author = userRepository.findById(userId).orElse(null);
        return assembleViews(List.of(comment), userId,
                author == null ? Map.of() : Map.of(userId, author), Map.of()).get(0);
    }

    /** 본인 댓글만 지운다. 경로의 밑줄에 달리지 않은 댓글은 없는 것으로 본다. 답글은 DB CASCADE 로 함께 사라진다. */
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

    /** 경로의 밑줄에 달린 댓글만 찾는다 — 다른 밑줄의 댓글 id 는 없는 것으로 본다. */
    private QuoteComment requireComment(Long quoteId, Long commentId) {
        return commentRepository.findById(commentId)
                .filter(found -> found.belongsTo(quoteId))
                .orElseThrow(() -> ApiException.of(ErrorCode.QUOTE_COMMENT_NOT_FOUND));
    }

    private Map<Long, User> loadAuthors(List<QuoteComment> comments) {
        List<Long> ids = comments.stream().map(QuoteComment::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Long> loadReplyCounts(List<QuoteComment> comments) {
        List<Long> ids = comments.stream().map(QuoteComment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countPerParent(ids).stream()
                .collect(Collectors.toMap(ReplyCount::getParentId, ReplyCount::getReplyCount));
    }

    /** 1단계까지만 — 답글에 다시 답글을 달려 하면 막는다(ClubPostService.create 선례). */
    static QuoteComment requireRepliable(QuoteComment parent) {
        if (parent.isReply()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "답글에는 답글을 달 수 없습니다.");
        }
        return parent;
    }

    /** 배치 맵으로 뷰를 조립한다(QuoteService.assembleViews 선례). 탈퇴한 작성자는 "알 수 없음", 답글 수는 결측 시 0. */
    static List<QuoteCommentView> assembleViews(List<QuoteComment> comments, Long viewerId,
                                                Map<Long, User> authors, Map<Long, Long> replyCounts) {
        return comments.stream()
                .map(comment -> {
                    User author = authors.get(comment.getUserId());
                    return new QuoteCommentView(
                            comment.getId(),
                            comment.getQuoteId(),
                            comment.getParentId(),
                            comment.getUserId(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            author == null ? null : author.getAvatarUrl(),
                            comment.getBody(),
                            comment.isOwnedBy(viewerId),
                            replyCounts.getOrDefault(comment.getId(), 0L),
                            comment.getCreatedAt() == null ? Instant.now() : comment.getCreatedAt());
                })
                .toList();
    }
}
