package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.CreatePostCommentRequest;
import app.bookey.api.post.dto.PostDtos.PostCommentView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.post.PostComment;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 독후감 댓글 — 목록(오래된 순) · 작성(답글 1단계) · 본인 삭제. QuoteCommentService 미러. */
@Service
@RequiredArgsConstructor
public class PostCommentService {

    /** 도배 방지 — 1분에 20건. */
    private static final int CREATE_RATE_LIMIT = 20;

    private final PostCommentRepository commentRepository;
    /** 읽기 권한 규칙은 PostService.readable 하나만 쓴다 — 규칙이 둘로 갈라지지 않게. */
    private final PostService postService;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    /**
     * 댓글 목록 — 루트만 페이지로 끊고 답글은 각 루트에 묶어 함께 내린다.
     * 오래된 순이라 입력창이 아래에 있어도 새 댓글이 바로 위에 보인다.
     */
    @Transactional(readOnly = true)
    public PageResponse<PostCommentView> list(Long viewerId, Long postId, Pageable pageable) {
        postService.readable(viewerId, postId);
        Page<PostComment> page =
                commentRepository.findAllByPostIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(postId, pageable);
        List<PostComment> roots = page.getContent();
        Map<Long, List<PostComment>> repliesByParent = loadReplies(roots);

        // 작성자는 루트와 답글을 한 번에 읽는다(건당 findById 금지).
        List<PostComment> all = new ArrayList<>(roots);
        repliesByParent.values().forEach(all::addAll);

        List<PostCommentView> views = assembleThreads(roots, repliesByParent, viewerId, loadAuthors(all));
        // totalElements 는 루트 수 — 답글은 루트에 딸려 나가므로 페이지 크기 계산에 넣지 않는다.
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** parentId 가 있으면 답글 — 답글의 답글은 막는다(2단계까지). */
    @Transactional
    public PostCommentView create(Long userId, Long postId, CreatePostCommentRequest request) {
        postService.readable(userId, postId);
        rateLimiter.require("post:comment:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        Long parentId = null;
        if (request.parentId() != null) {
            PostComment parent = commentRepository.findById(request.parentId())
                    .filter(found -> found.belongsTo(postId))
                    .orElseThrow(() -> ApiException.of(ErrorCode.POST_COMMENT_NOT_FOUND));
            if (!PostComment.canReplyTo(parent)) {
                throw ApiException.of(ErrorCode.COMMENT_REPLY_DEPTH);
            }
            parentId = parent.getId();
        }

        PostComment comment = commentRepository.save(PostComment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(parentId)
                .body(request.body().trim())
                .build());

        User author = userRepository.findById(userId).orElse(null);
        return assembleThreads(List.of(comment), Map.of(), userId,
                author == null ? Map.of() : Map.of(userId, author)).get(0);
    }

    /** 본인 댓글만 지운다. 경로의 독후감에 달리지 않은 댓글은 없는 것으로 본다. 답글은 DB CASCADE 로 함께 지워진다. */
    @Transactional
    public void delete(Long userId, Long postId, Long commentId) {
        PostComment comment = commentRepository.findById(commentId)
                .filter(found -> found.belongsTo(postId))
                .orElseThrow(() -> ApiException.of(ErrorCode.POST_COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    /** 루트 id 들로 답글을 한 번에 읽어 부모별로 묶는다 — 각 목록은 오래된 순 그대로다. */
    private Map<Long, List<PostComment>> loadReplies(List<PostComment> roots) {
        List<Long> rootIds = roots.stream().map(PostComment::getId).toList();
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findAllByParentIdInOrderByCreatedAtAscIdAsc(rootIds).stream()
                .collect(Collectors.groupingBy(PostComment::getParentId));
    }

    private Map<Long, User> loadAuthors(List<PostComment> comments) {
        List<Long> ids = comments.stream().map(PostComment::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * 배치 맵으로 2단계 스레드를 조립한다(QuoteCommentService.assembleViews 선례).
     * 루트 순서는 그대로, 답글은 넘겨받은 순서(오래된 순)를 지키고 답글 행의 replies 는 빈 목록이다.
     * 탈퇴한 작성자는 "알 수 없음".
     */
    static List<PostCommentView> assembleThreads(List<PostComment> roots,
                                                 Map<Long, List<PostComment>> repliesByParent,
                                                 Long viewerId, Map<Long, User> authors) {
        return roots.stream()
                .map(root -> toView(root, viewerId, authors,
                        repliesByParent.getOrDefault(root.getId(), List.of()).stream()
                                .map(reply -> toView(reply, viewerId, authors, List.of()))
                                .toList()))
                .toList();
    }

    private static PostCommentView toView(PostComment comment, Long viewerId,
                                          Map<Long, User> authors, List<PostCommentView> replies) {
        User author = authors.get(comment.getUserId());
        return new PostCommentView(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getUserId(),
                author == null ? "알 수 없음" : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                comment.getBody(),
                comment.isOwnedBy(viewerId),
                comment.getCreatedAt() == null ? Instant.now() : comment.getCreatedAt(),
                replies);
    }
}
