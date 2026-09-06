package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.FollowCodeView;
import app.bookey.api.social.dto.SocialDtos.FollowUserView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PublicIdGenerator;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.social.UserPublicId;
import app.bookey.domain.social.UserPublicIdRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 팔로우 계약 단위 테스트 (§14.3) — 경로는 코드와 상호 엽서뿐, 코드 팔로우는 즉시 맞팔로우. */
class FollowServiceTest {

    private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
    private final UserPublicIdRepository publicIdRepository = mock(UserPublicIdRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = mock(Clock.class);
    private final FollowService service =
            new FollowService(followRepository, publicIdRepository, userRepository, clock);

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("내 코드 — 없으면 16자리로 만들고, 딥링크를 함께 준다")
    void myCodeCreatesWhenMissing() {
        when(publicIdRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(publicIdRepository.save(any(UserPublicId.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowCodeView view = service.myCode(1L);

        assertThat(view.code()).hasSize(16);
        assertThat(PublicIdGenerator.isValidFormat(view.code())).isTrue();
        assertThat(view.deepLink()).isEqualTo("https://bookey.app/u/" + view.code());
    }

    @Test
    @DisplayName("코드 회전 — 새 16자리 코드로 바뀐다 (유출 무효화)")
    void rotateChangesCode() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        UserPublicId publicId = new UserPublicId(1L, "AAAABBBBCCCCDDDD");
        when(publicIdRepository.findByUserId(1L)).thenReturn(Optional.of(publicId));

        FollowCodeView view = service.rotate(1L);

        assertThat(view.code()).isNotEqualTo("AAAABBBBCCCCDDDD").hasSize(16);
        assertThat(publicId.getRotatedAt()).isNotNull();
    }

    @Test
    @DisplayName("코드 팔로우 — 즉시 맞팔로우(양방향 두 행), 소문자·하이픈 입력도 받아준다")
    void followByCodeCreatesMutual() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        when(publicIdRepository.findByCode("AAAABBBBCCCCDDDD"))
                .thenReturn(Optional.of(new UserPublicId(2L, "AAAABBBBCCCCDDDD")));
        User target = User.builder().handle("friend").email("f@dev.local").nickname("친구").build();
        set(target, "id", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        FollowUserView view = service.followByCode(1L, "aaaa-bbbb-cccc-dddd");

        assertThat(view.userId()).isEqualTo(2L);
        assertThat(view.mutual()).isTrue();
        ArgumentCaptor<UserFollow> saved = ArgumentCaptor.forClass(UserFollow.class);
        verify(followRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(UserFollow::getFollowerId).containsExactly(1L, 2L);
        assertThat(saved.getAllValues()).extracting(UserFollow::getFolloweeId).containsExactly(2L, 1L);
        assertThat(saved.getAllValues()).extracting(UserFollow::getSource).containsOnly(FollowSource.CODE);
    }

    @Test
    @DisplayName("코드 팔로우 — 내 코드는 FOLLOW_SELF, 없는 코드·형식 불량은 FOLLOW_CODE_INVALID, 이미 맞팔이면 ALREADY_FOLLOWING")
    void followByCodeGuards() {
        when(publicIdRepository.findByCode("AAAABBBBCCCCDDDD"))
                .thenReturn(Optional.of(new UserPublicId(1L, "AAAABBBBCCCCDDDD")));
        assertThatThrownBy(() -> service.followByCode(1L, "AAAABBBBCCCCDDDD"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_SELF);

        assertThatThrownBy(() -> service.followByCode(1L, "too-short"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_CODE_INVALID);

        when(publicIdRepository.findByCode("EEEEFFFFGGGGHHHH"))
                .thenReturn(Optional.of(new UserPublicId(2L, "EEEEFFFFGGGGHHHH")));
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(2L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.followByCode(1L, "EEEEFFFFGGGGHHHH"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_FOLLOWING);
        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("ensureMutual — 이미 있는 방향은 두고 없는 방향만 만든다 (엽서 답장 경로)")
    void ensureMutualFillsMissingDirection() {
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(2L, 1L)).thenReturn(false);

        service.ensureMutual(1L, 2L, FollowSource.POSTCARD);

        ArgumentCaptor<UserFollow> saved = ArgumentCaptor.forClass(UserFollow.class);
        verify(followRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getFollowerId()).isEqualTo(2L);
        assertThat(saved.getValue().getFolloweeId()).isEqualTo(1L);
        assertThat(saved.getValue().getSource()).isEqualTo(FollowSource.POSTCARD);
    }

    @Test
    @DisplayName("언팔로우 — 내 방향만 끊고 상대의 팔로우는 남긴다")
    void unfollowRemovesOnlyMyDirection() {
        UserFollow mine = UserFollow.builder().followerId(1L).followeeId(2L).source(FollowSource.CODE).build();
        when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(mine));

        service.unfollow(1L, 2L);

        verify(followRepository).delete(mine);
        verify(followRepository, never()).findByFollowerIdAndFolloweeId(2L, 1L);
    }

    @Test
    @DisplayName("팔로잉 목록 — 상대가 나를 팔로우하는지(역방향)로 맞팔을 판정한다")
    void followingListMutualFlag() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        UserFollow follow = UserFollow.builder().followerId(1L).followeeId(2L).source(FollowSource.CODE).build();
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
