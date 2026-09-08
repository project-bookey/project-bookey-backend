package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowCodeView;
import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.PublicIdGenerator;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.social.UserPublicId;
import app.bookey.domain.social.UserPublicIdRepository;
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
 * 팔로우 (§14.3) — 경로는 상호 엽서와 16자리 코드(QR)뿐. 검색·팔로우 버튼은 없다.
 * 코드를 보여주는 행위 = 수락으로 보고, 코드 팔로우는 즉시 맞팔로우를 만든다(지인 전제).
 * 코드가 유출되면 회전으로 무효화한다(모임 초대 코드와 같은 철학).
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserFollowRepository followRepository;
    private final UserPublicIdRepository publicIdRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    /** 내 팔로우 코드 — 없으면 만든다. QR 은 클라이언트가 딥링크로 그린다. */
    @Transactional
    public FollowCodeView myCode(Long userId) {
        UserPublicId publicId = publicIdRepository.findByUserId(userId)
                .orElseGet(() -> publicIdRepository.save(new UserPublicId(userId, uniqueCode())));
        return toCodeView(publicId);
    }

    @Transactional
    public FollowCodeView rotate(Long userId) {
        UserPublicId publicId = publicIdRepository.findByUserId(userId)
                .orElseGet(() -> publicIdRepository.save(new UserPublicId(userId, uniqueCode())));
        publicId.rotate(uniqueCode(), Instant.now(clock));
        return toCodeView(publicId);
    }

    /** 코드로 팔로우 — 즉시 맞팔로우. */
    @Transactional
    public FollowUserView followByCode(Long userId, String rawCode) {
        String code = PublicIdGenerator.normalize(rawCode);
        if (!PublicIdGenerator.isValidFormat(code)) {
            throw ApiException.of(ErrorCode.FOLLOW_CODE_INVALID);
        }
        UserPublicId target = publicIdRepository.findByCode(code)
                .orElseThrow(() -> ApiException.of(ErrorCode.FOLLOW_CODE_INVALID));
        if (target.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FOLLOW_SELF);
        }
        boolean iFollow = followRepository.existsByFollowerIdAndFolloweeId(userId, target.getUserId());
        boolean followsMe = followRepository.existsByFollowerIdAndFolloweeId(target.getUserId(), userId);
        if (iFollow && followsMe) {
            throw ApiException.of(ErrorCode.ALREADY_FOLLOWING);
        }
        ensureMutual(userId, target.getUserId(), FollowSource.CODE);
        User user = userRepository.findById(target.getUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new FollowUserView(user.getId(), user.getNickname(), user.getAvatarUrl(),
                true, Instant.now(clock));
    }

    /** 양방향 팔로우를 보장한다 — 이미 있는 방향은 그대로 둔다. 엽서 답장 성립 시에도 쓰인다. */
    @Transactional
    public void ensureMutual(Long a, Long b, FollowSource source) {
        boolean createdAtoB = false;
        boolean createdBtoA = false;
        if (!followRepository.existsByFollowerIdAndFolloweeId(a, b)) {
            followRepository.save(UserFollow.builder().followerId(a).followeeId(b).source(source).build());
            createdAtoB = true;
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(b, a)) {
            followRepository.save(UserFollow.builder().followerId(b).followeeId(a).source(source).build());
            createdBtoA = true;
        }
        if (createdAtoB) {
            notifyConnected(b, a);
        }
        if (createdBtoA) {
            notifyConnected(a, b);
        }
    }

    @Transactional(readOnly = true)
    public boolean isMutual(Long a, Long b) {
        return followRepository.existsByFollowerIdAndFolloweeId(a, b)
                && followRepository.existsByFollowerIdAndFolloweeId(b, a);
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

    private String uniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = PublicIdGenerator.generate();
            if (!publicIdRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.of(ErrorCode.INTERNAL_ERROR);
    }

    private void notifyConnected(Long userId, Long counterpartId) {
        User counterpart = userRepository.findById(counterpartId).orElse(null);
        String nickname = counterpart == null ? "상대" : counterpart.getNickname();
        notificationService.inApp(new NotificationService.NotificationRequest(
                userId, NotificationType.FOLLOW_CONNECTED, null, null, null,
                "서로 연결됐어요",
                nickname + "님과 맞팔로우가 되었습니다.",
                Map.of("userId", counterpartId), null));
    }

    private static FollowCodeView toCodeView(UserPublicId publicId) {
        return new FollowCodeView(publicId.getCode(), "https://bookey.app/u/" + publicId.getCode());
    }
}
