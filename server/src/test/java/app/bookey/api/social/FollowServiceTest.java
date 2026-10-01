package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 팔로우 계약 단위 테스트 (§14.3) — 버튼 한 번, 한 방향. */
class FollowServiceTest {

    private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = mock(Clock.class);
    private final FollowService service =
            new FollowService(followRepository, userRepository, notificationService, clock);

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User user(long id, String nickname) {
        User u = User.builder().handle("h" + id).email(id + "@dev.local").nickname(nickname).build();
        set(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("팔로우 — 한 방향 한 행(BUTTON)만 만들고, 상대에게 '새 팔로워' 알림")
    void followCreatesOneDirection() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-01T03:00:00Z"));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "친구")));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "나")));
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(UserFollow.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUserView view = service.follow(1L, 2L);

        assertThat(view.userId()).isEqualTo(2L);
        assertThat(view.mutual()).isFalse();
        ArgumentCaptor<UserFollow> saved = ArgumentCaptor.forClass(UserFollow.class);
        verify(followRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getFollowerId()).isEqualTo(1L);
        assertThat(saved.getValue().getFolloweeId()).isEqualTo(2L);
        assertThat(saved.getValue().getSource()).isEqualTo(FollowSource.BUTTON);
        ArgumentCaptor<NotificationService.NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationService.NotificationRequest.class);
        verify(notificationService).inApp(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.FOLLOWED);
    }

    @Test
    @DisplayName("팔로우 — 상대가 이미 나를 팔로우 중이면 맞팔로우, '연결됐어요' 알림")
    void followBackBecomesMutual() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-01T03:00:00Z"));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "친구")));
        when(followRepository.existsByFollowerIdAndFolloweeId(2L, 1L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(UserFollow.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUserView view = service.follow(1L, 2L);

        assertThat(view.mutual()).isTrue();
        ArgumentCaptor<NotificationService.NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationService.NotificationRequest.class);
        verify(notificationService).inApp(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.FOLLOW_CONNECTED);
    }

    @Test
    @DisplayName("팔로우 — 이미 팔로우 중이면 그대로 성공(저장·알림 없음), 자기 자신은 FOLLOW_SELF")
    void followIsIdempotentAndGuardsSelf() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-01T03:00:00Z"));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "친구")));
        UserFollow existing = UserFollow.builder().followerId(1L).followeeId(2L).source(FollowSource.BUTTON).build();
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(existing));

        assertThat(service.follow(1L, 2L).userId()).isEqualTo(2L);
        verify(followRepository, never()).save(any());
        verify(notificationService, never()).inApp(any());

        assertThatThrownBy(() -> service.follow(1L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_SELF);
    }

    @Test
    @DisplayName("팔로우 — 없는 사용자는 NOT_FOUND")
    void followUnknownUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.follow(1L, 9L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("팔로잉 id — 저장소가 준 id 를 그대로 싣는다")
    void followingIds() {
        when(followRepository.findFolloweeIdsByFollowerId(1L)).thenReturn(List.of(2L, 3L));
        assertThat(service.followingIds(1L).ids()).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("언팔로우 — 내 방향만 끊고 상대의 팔로우는 남긴다")
    void unfollowRemovesOnlyMyDirection() {
        UserFollow mine = UserFollow.builder().followerId(1L).followeeId(2L).source(FollowSource.BUTTON).build();
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(mine));

        service.unfollow(1L, 2L);

        verify(followRepository).delete(mine);
        verify(followRepository, never()).findByFollowerIdAndFolloweeId(2L, 1L);
    }

    @Test
    @DisplayName("팔로잉 목록 — 상대가 나를 팔로우하는지(역방향)로 맞팔을 판정한다")
    void followingListMutualFlag() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        UserFollow follow = UserFollow.builder().followerId(1L).followeeId(2L).source(FollowSource.BUTTON).build();
        when(followRepository.findAllByFollowerIdOrderByIdDesc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(follow)));
        when(followRepository.findAllByFolloweeIdAndFollowerIdIn(any(), any())).thenReturn(List.of());
        User target = User.builder().handle("friend").email("f@dev.local").nickname("친구").build();
        set(target, "id", 2L);
        when(userRepository.findAllById(any())).thenReturn(List.of(target));

        List<FollowUserView> content = service.following(1L,
                org.springframework.data.domain.PageRequest.of(0, 20)).content();

        assertThat(content).hasSize(1);
        assertThat(content.get(0).mutual()).isFalse(); // 상대는 나를 언팔로우한 상태
    }
}
