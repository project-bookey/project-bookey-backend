package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.PostcardView;
import app.bookey.api.social.dto.SocialDtos.ReplyPostcardRequest;
import app.bookey.api.social.dto.SocialDtos.SendPostcardRequest;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.GraphemeCounter;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.Postcard;
import app.bookey.domain.social.PostcardRepository;
import app.bookey.domain.social.PostcardStatus;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransactionKind;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 엽서 (§14.2) — 16글자로 자신을 어필하는 유일한 연결 요청.
 * 발송: 무료 일 5장(KST 자정 리셋) → 보유 엽서. 답장: 우표 1개(동봉 엽서는 무료).
 * 답장이 성립하면 자동 맞팔로우.
 */
@Service
@RequiredArgsConstructor
public class PostcardService {

    private final PostcardRepository postcardRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final WalletService walletService;
    private final FollowService followService;
    private final BookeyProperties properties;
    private final Clock clock;

    @Transactional
    public PostcardView send(Long fromUserId, SendPostcardRequest request) {
        if (fromUserId.equals(request.toUserId())) {
            throw ApiException.of(ErrorCode.POSTCARD_SELF);
        }
        String body = requireBody(request.body());
        User to = userRepository.findById(request.toUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // 글 컨텍스트는 수신자의 글이어야 한다 — "이 글을 보고 보냈다"가 성립하도록.
        if (request.postId() != null) {
            Post post = postRepository.findById(request.postId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.POST_NOT_FOUND));
            if (!post.isOwnedBy(to.getId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "이 독후감은 받는 사람의 글이 아닙니다.");
            }
        }
        if (postcardRepository.existsByFromUserIdAndToUserIdAndStatus(
                fromUserId, to.getId(), PostcardStatus.SENT)) {
            throw ApiException.of(ErrorCode.POSTCARD_ALREADY_SENT);
        }

        Wallet wallet = walletService.prepared(fromUserId);
        Postcard postcard = postcardRepository.save(Postcard.builder()
                .fromUserId(fromUserId).toUserId(to.getId())
                .postId(request.postId()).body(body)
                .stampAttached(request.attachStamp())
                .build());
        // 우표 동봉을 먼저 — 우표가 없으면 엽서 비용도 쓰기 전에 전부 롤백된다.
        if (request.attachStamp()) {
            walletService.payStamp(fromUserId, wallet, WalletTransactionKind.ATTACH_STAMP, postcard.getId());
        }
        walletService.payPostcardSend(fromUserId, wallet, postcard.getId());
        return toView(postcard, fromUserId);
    }

    /** 답장 — 우표 1개 소모(동봉 엽서는 무료). 성립하면 자동 맞팔로우. */
    @Transactional
    public PostcardView reply(Long userId, Long postcardId, ReplyPostcardRequest request) {
        Postcard postcard = postcardRepository.findById(postcardId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POSTCARD_NOT_FOUND));
        if (!postcard.isRecipient(userId)) {
            // 남의 엽서는 존재도 드러내지 않는다.
            throw ApiException.of(ErrorCode.POSTCARD_NOT_FOUND);
        }
        if (postcard.isReplied()) {
            throw ApiException.of(ErrorCode.POSTCARD_ALREADY_REPLIED);
        }
        String body = requireBody(request.body());
        if (!postcard.isStampAttached()) {
            Wallet wallet = walletService.prepared(userId);
            walletService.payStamp(userId, wallet, WalletTransactionKind.REPLY_STAMP, postcard.getId());
        }
        postcard.reply(body, Instant.now(clock));
        followService.ensureMutual(postcard.getFromUserId(), postcard.getToUserId(), FollowSource.POSTCARD);
        return toView(postcard, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostcardView> inbox(Long userId, Pageable pageable) {
        return toPage(postcardRepository.findAllByToUserIdOrderByIdDesc(userId, pageable), userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostcardView> sent(Long userId, Pageable pageable) {
        return toPage(postcardRepository.findAllByFromUserIdOrderByIdDesc(userId, pageable), userId);
    }

    /** 보낸 사람 또는 받은 사람만 지울 수 있다 — 남의 엽서는 존재도 드러내지 않는다. */
    @Transactional
    public void delete(Long userId, Long postcardId) {
        Postcard postcard = postcardRepository.findById(postcardId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POSTCARD_NOT_FOUND));
        if (!postcard.isParticipant(userId)) {
            throw ApiException.of(ErrorCode.POSTCARD_NOT_FOUND);
        }
        postcardRepository.delete(postcard);
    }

    /** 16글자(grapheme) 검사 — §14.9 확정: 한글 완성형 글자 기준. */
    private String requireBody(String raw) {
        String body = raw.trim();
        if (GraphemeCounter.count(body) > properties.social().postcardMaxLength()) {
            throw ApiException.of(ErrorCode.POSTCARD_BODY_TOO_LONG);
        }
        return body;
    }

    // ────────────────────────────── 조립 ──────────────────────────────

    private PageResponse<PostcardView> toPage(Page<Postcard> page, Long viewerId) {
        List<Postcard> cards = page.getContent();
        Map<Long, User> users = loadUsers(cards);
        Map<Long, Post> posts = loadPosts(cards);
        return PageResponse.of(page, card -> toView(card, viewerId, users, posts));
    }

    private PostcardView toView(Postcard card, Long viewerId) {
        return toView(card, viewerId, loadUsers(List.of(card)), loadPosts(List.of(card)));
    }

    private Map<Long, User> loadUsers(List<Postcard> cards) {
        List<Long> ids = cards.stream()
                .flatMap(card -> Stream.of(card.getFromUserId(), card.getToUserId()))
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Post> loadPosts(List<Postcard> cards) {
        List<Long> ids = cards.stream().map(Postcard::getPostId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return postRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
    }

    private PostcardView toView(Postcard card, Long viewerId, Map<Long, User> users, Map<Long, Post> posts) {
        User from = users.get(card.getFromUserId());
        User to = users.get(card.getToUserId());
        Post post = card.getPostId() == null ? null : posts.get(card.getPostId());
        return new PostcardView(
                card.getId(),
                card.getFromUserId(),
                from == null ? "알 수 없음" : from.getNickname(),
                from == null ? null : from.getAvatarUrl(),
                card.getToUserId(),
                to == null ? "알 수 없음" : to.getNickname(),
                to == null ? null : to.getAvatarUrl(),
                card.getPostId(),
                post == null ? null : post.getTitle(),
                card.getBody(),
                card.isStampAttached(),
                card.getStatus(),
                card.getReplyBody(),
                card.getRepliedAt(),
                card.getFromUserId().equals(viewerId),
                card.getCreatedAt() == null ? Instant.now(clock) : card.getCreatedAt());
    }
}
