package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.api.social.dto.SocialDtos.FollowingIdsView;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 팔로우 (§14.3) — 한 방향. 프로필·피드·댓글의 팔로우 버튼 한 번으로 바로 성립하고,
 * 서로 하면 맞팔로우다. 채팅과는 무관하다(채팅은 엽서 답장 기준 — ChatService).
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserFollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    /** 팔로우 — 이미 팔로우 중이면 아무것도 하지 않는다(멱등). 새로 성립하면 상대에게 알린다. */
    @Transactional
    public FollowUserView follow(Long userId, Long targetUserId) {
        if (targetUserId.equals(userId)) {
            throw ApiException.of(ErrorCode.FOLLOW_SELF);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        boolean followsMe = followRepository.existsByFollowerIdAndFolloweeId(targetUserId, userId);
        UserFollow follow = followRepository.findByFollowerIdAndFolloweeId(userId, targetUserId)
                .orElseGet(() -> {
                    UserFollow created = followRepository.save(UserFollow.builder()
                            .followerId(userId).followeeId(targetUserId).source(FollowSource.BUTTON).build());
                    notifyFollowed(targetUserId, userId, followsMe);
                    return created;
                });
        return new FollowUserView(target.getId(), target.getNickname(), target.getAvatarUrl(), followsMe,
                follow.getCreatedAt() == null ? Instant.now(clock) : follow.getCreatedAt());
    }

    /** 내가 팔로우하는 사람 id 전부 — 피드·댓글의 버튼 상태 판정용. */
    @Transactional(readOnly = true)
    public FollowingIdsView followingIds(Long userId) {
        return new FollowingIdsView(followRepository.findFolloweeIdsByFollowerId(userId));
    }

    /** 나를 팔로우하는 사람들 — 상대 정보와 맞팔 여부(내가 그들을 팔로우하는가) 포함. */
    @Transactional(readOnly = true)
    public PageResponse<FollowUserView> followers(Long userId, Pageable pageable) {
        Page<UserFollow> page = followRepository.findAllByFolloweeIdOrderByIdDesc(userId, pageable);
        List<Long> ids = page.getContent().stream().map(UserFollow::getFollowerId).distinct().toList();
        Set<Long> mutualIds = ids.isEmpty() ? Set.of()
                : followRepository.findAllByFollowerIdAndFolloweeIdIn(userId, ids).stream()
                        .map(UserFollow::getFolloweeId)
                        .collect(Collectors.toSet());
        return toUserPage(page, UserFollow::getFollowerId, mutualIds);
    }

    /** 내가 팔로우하는 사람들 — 맞팔 여부(그들이 나를 팔로우하는가) 포함. */
    @Transactional(readOnly = true)
    public PageResponse<FollowUserView> following(Long userId, Pageable pageable) {
        Page<UserFollow> page = followRepository.findAllByFollowerIdOrderByIdDesc(userId, pageable);
        List<Long> ids = page.getContent().stream().map(UserFollow::getFolloweeId).distinct().toList();
        Set<Long> mutualIds = ids.isEmpty() ? Set.of()
                : followRepository.findAllByFolloweeIdAndFollowerIdIn(userId, ids).stream()
                        .map(UserFollow::getFollowerId)
                        .collect(Collectors.toSet());
        return toUserPage(page, UserFollow::getFolloweeId, mutualIds);
    }

    /** 언팔로우 — 내 방향만 끊는다. 상대의 팔로우는 남는다. */
    @Transactional
    public void unfollow(Long userId, Long targetUserId) {
        followRepository.findByFollowerIdAndFolloweeId(userId, targetUserId)
                .ifPresent(followRepository::delete);
    }

    private PageResponse<FollowUserView> toUserPage(Page<UserFollow> page,
                                                    Function<UserFollow, Long> counterpart,
                                                    Set<Long> mutualIds) {
        List<Long> userIds = page.getContent().stream().map(counterpart).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, follow -> {
            Long otherId = counterpart.apply(follow);
            User user = users.get(otherId);
            return new FollowUserView(otherId,
                    user == null ? "알 수 없음" : user.getNickname(),
                    user == null ? null : user.getAvatarUrl(),
                    mutualIds.contains(otherId),
                    follow.getCreatedAt() == null ? Instant.now(clock) : follow.getCreatedAt());
        });
    }

    /** 상대에게 알린다 — 맞팔로우가 되면 '맞팔로우가 됐어요', 아니면 '팔로우했어요'. */
    private void notifyFollowed(Long userId, Long followerId, boolean mutual) {
        User follower = userRepository.findById(followerId).orElse(null);
        String nickname = follower == null ? "누군가" : follower.getNickname();
        notificationService.inApp(new NotificationService.NotificationRequest(
                userId, mutual ? NotificationType.FOLLOW_CONNECTED : NotificationType.FOLLOWED, null, null, null,
                mutual ? "맞팔로우가 됐어요" : "새 팔로워가 생겼어요",
                mutual ? nickname + "님과 서로 팔로우하게 됐어요." : nickname + "님이 나를 팔로우했어요.",
                Map.of("userId", followerId), null));
    }
}
