package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.LikerView;
import app.bookey.api.social.dto.SocialDtos.UserProfileView;
import app.bookey.api.social.dto.SocialDtos.VisitorView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostLike;
import app.bookey.domain.post.PostLikeRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.social.ProfileVisit;
import app.bookey.domain.social.ProfileVisitRepository;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 마이페이지 (§14.2·14.3).
 * 방문 수는 전체 공개, 방문자 목록·좋아요 누른 사람 목록은 구독 회원 전용 —
 * "신호가 왔다는 사실은 모두에게, 신호의 발신자는 구독 회원에게".
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final ProfileVisitRepository visitRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    /** 유저 프로필 — 남의 프로필을 열면 방문 기록이 남는다(방문자·날짜당 1건, KST). */
    @Transactional
    public UserProfileView profile(Long viewerId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        boolean me = userId.equals(viewerId);
        if (!me) {
            recordVisit(viewerId, userId);
        }
        boolean iFollow = !me && followRepository.existsByFollowerIdAndFolloweeId(viewerId, userId);
        boolean followsMe = !me && followRepository.existsByFollowerIdAndFolloweeId(userId, viewerId);
        return new UserProfileView(
                user.getId(), user.getNickname(), user.getAvatarUrl(), user.getHandle(),
                followRepository.countByFolloweeId(userId),
                followRepository.countByFollowerId(userId),
                visitRepository.countByHostId(userId),
                postRepository.countByUserIdAndVisibility(userId, PostVisibility.PUBLIC),
                iFollow, followsMe, iFollow && followsMe, me);
    }

    /** 같은 방문자가 하루에 여러 번 열어도 1건 — 유니크 제약 충돌은 조용히 무시한다. */
    private void recordVisit(Long visitorId, Long hostId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        if (visitRepository.existsByVisitorIdAndHostIdAndVisitDate(visitorId, hostId, today)) {
            return;
        }
        try {
            visitRepository.save(new ProfileVisit(visitorId, hostId, today));
        } catch (DataIntegrityViolationException ignored) {
            // 동시 요청이 같은 날짜 행을 먼저 넣은 경우 — 방문은 이미 기록돼 있다.
        }
    }

    /** 내 방문자 목록 — 구독 회원 전용 (§14.2). */
    @Transactional(readOnly = true)
    public PageResponse<VisitorView> visitors(Long userId, Pageable pageable) {
        requireSubscription(userId);
        Page<ProfileVisit> page = visitRepository.findAllByHostIdOrderByIdDesc(userId, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream()
                .map(ProfileVisit::getVisitorId).distinct().toList());
        return PageResponse.of(page, visit -> {
            User visitor = users.get(visit.getVisitorId());
            return new VisitorView(visit.getVisitorId(),
                    visitor == null ? "알 수 없음" : visitor.getNickname(),
                    visitor == null ? null : visitor.getAvatarUrl(),
                    visit.getCreatedAt() == null ? Instant.now(clock) : visit.getCreatedAt());
        });
    }

    /** 내 글에 좋아요 누른 사람 목록 — 글 주인 + 구독 회원 전용. 숫자는 PostView 로 모두에게 공개. */
    @Transactional(readOnly = true)
    public PageResponse<LikerView> likers(Long viewerId, Long postId, Pageable pageable) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(viewerId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        requireSubscription(viewerId);
        Page<PostLike> page = likeRepository.findAllByPostIdOrderByIdDesc(postId, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream()
                .map(PostLike::getUserId).distinct().toList());
        return PageResponse.of(page, like -> {
            User liker = users.get(like.getUserId());
            return new LikerView(like.getUserId(),
                    liker == null ? "알 수 없음" : liker.getNickname(),
                    liker == null ? null : liker.getAvatarUrl(),
                    like.getCreatedAt() == null ? Instant.now(clock) : like.getCreatedAt());
        });
    }

    private void requireSubscription(Long userId) {
        if (!subscriptionService.isActive(userId)) {
            throw ApiException.of(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
