package app.bookey.api.review;

import app.bookey.api.review.dto.ReviewDtos.CreateReviewCommentRequest;
import app.bookey.api.review.dto.ReviewDtos.ReviewCommentView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewComment;
import app.bookey.domain.review.ReviewCommentRepository;
import app.bookey.domain.review.ReviewCommentRepository.ReplyCount;
import app.bookey.domain.review.ReviewRepository;
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

/** 리뷰에 덧붙인 말(댓글) — 목록(최상위, 오래된 순) · 답글(1단계) · 작성 · 본인 삭제(QuoteCommentService 미러). */
@Service
@RequiredArgsConstructor
public class ReviewCommentService {

    /** 도배 방지 — 1분에 20건. */
    private static final int CREATE_RATE_LIMIT = 20;

    private final ReviewCommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    /**
     * 댓글 목록 — 최상위만, 오래된 순. 입력창이 아래에 있으므로 새 댓글이 바로 위에 보인다.
     * 답글은 접어두고 replyCount 만 실어 보낸다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewCommentView> list(Long userId, Long reviewId, Pageable pageable) {
        requireVisibleReview(reviewId);
        Page<ReviewComment> page =
                commentRepository.findAllByReviewIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(reviewId, pageable);
        List<ReviewComment> comments = page.getContent();
        List<ReviewCommentView> views =
                assembleViews(comments, userId, loadAuthors(comments), loadReplyCounts(comments));
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** 한 댓글의 답글 목록 — 오래된 순. 답글에는 답글이 없으므로 replyCount 는 모두 0이다. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewCommentView> replies(Long userId, Long reviewId, Long commentId, Pageable pageable) {
        requireVisibleReview(reviewId);
        ReviewComment comment = requireComment(reviewId, commentId);
        if (comment.isReply()) {
            // 1단계 모델 — 답글은 답글을 가질 수 없으니 없는 것으로 본다.
            throw ApiException.of(ErrorCode.REVIEW_COMMENT_NOT_FOUND);
        }
        Page<ReviewComment> page = commentRepository.findAllByParentIdOrderByCreatedAtAscIdAsc(commentId, pageable);
        List<ReviewComment> replies = page.getContent();
        List<ReviewCommentView> views = assembleViews(replies, userId, loadAuthors(replies), Map.of());
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** parentId 를 주면 그 댓글의 답글이 된다. 답글에는 답글을 달 수 없다(1단계). */
    @Transactional
    public ReviewCommentView create(Long userId, Long reviewId, CreateReviewCommentRequest request) {
        requireVisibleReview(reviewId);
        rateLimiter.require("review:comment:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        Long parentId = request.parentId() == null
                ? null
                : requireRepliable(requireComment(reviewId, request.parentId())).getId();

        ReviewComment comment = commentRepository.save(ReviewComment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .parentId(parentId)
                .body(request.body().trim())
                .build());

        User author = userRepository.findById(userId).orElse(null);
        return assembleViews(List.of(comment), userId,
                author == null ? Map.of() : Map.of(userId, author), Map.of()).get(0);
    }

    /**
     * 본인 댓글만 지운다. 경로의 리뷰에 달리지 않은 댓글은 없는 것으로 본다.
     * 리뷰가 숨겨지거나 지워져도 내가 쓴 말은 거둘 수 있어야 하므로 리뷰 상태는 보지 않는다.
     * 답글은 DB CASCADE 로 함께 사라진다.
     */
    @Transactional
    public void delete(Long userId, Long reviewId, Long commentId) {
        ReviewComment comment = requireComment(reviewId, commentId);
        if (!comment.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    /** 숨겨지거나 지워진 리뷰는 없는 것으로 본다 — 댓글도 달 수 없다. */
    private void requireVisibleReview(Long reviewId) {
        reviewRepository.findById(reviewId)
                .filter(Review::isVisible)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
    }

    /** 경로의 리뷰에 달린 댓글만 찾는다 — 다른 리뷰의 댓글 id 는 없는 것으로 본다. */
    private ReviewComment requireComment(Long reviewId, Long commentId) {
        return commentRepository.findById(commentId)
                .filter(found -> found.belongsTo(reviewId))
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_COMMENT_NOT_FOUND));
    }

    private Map<Long, User> loadAuthors(List<ReviewComment> comments) {
        List<Long> ids = comments.stream().map(ReviewComment::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Long> loadReplyCounts(List<ReviewComment> comments) {
        List<Long> ids = comments.stream().map(ReviewComment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countPerParent(ids).stream()
                .collect(Collectors.toMap(ReplyCount::getParentId, ReplyCount::getReplyCount));
    }

    /** 1단계까지만 — 답글에 다시 답글을 달려 하면 막는다(ClubPostService.create 선례). */
    static ReviewComment requireRepliable(ReviewComment parent) {
        if (parent.isReply()) {
            throw ApiException.of(ErrorCode.COMMENT_REPLY_DEPTH);
        }
        return parent;
    }

    /** 배치 맵으로 뷰를 조립한다(QuoteCommentService.assembleViews 미러). 탈퇴한 작성자는 "알 수 없음", 답글 수는 결측 시 0. */
    static List<ReviewCommentView> assembleViews(List<ReviewComment> comments, Long viewerId,
                                                 Map<Long, User> authors, Map<Long, Long> replyCounts) {
        return comments.stream()
                .map(comment -> {
                    User author = authors.get(comment.getUserId());
                    return new ReviewCommentView(
                            comment.getId(),
                            comment.getReviewId(),
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
