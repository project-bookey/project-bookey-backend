package app.bookey.api.social;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostLikeRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.social.ProfileVisit;
import app.bookey.domain.social.ProfileVisitRepository;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 마이페이지 단위 테스트 (§14.2) — 방문 기록(날짜당 1건, KST),
 * 방문자·좋아요 목록의 구독 게이트, 방문 수는 전체 공개.
 */
class ProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserFollowRepository followRepository = mock(UserFollowRepository.class);
    private final ProfileVisitRepository visitRepository = mock(ProfileVisitRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostLikeRepository likeRepository = mock(PostLikeRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final Clock clock = mock(Clock.class);
    private final ProfileService service = new ProfileService(
            userRepository, followRepository, visitRepository, postRepository,
            likeRepository, subscriptionService, clock);

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User host() {
        User user = User.builder().handle("host").email("host@dev.local").nickname("호스트").build();
        set(user, "id", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("남의 프로필 열람 — KST 날짜당 1건으로 방문이 기록되고, 방문 수는 누구에게나 응답된다")
    void profileRecordsVisitOncePerDay() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T14:59:00Z")); // KST 09-06 23:59
        host();
        when(visitRepository.countByHostId(2L)).thenReturn(7L);

        // 첫 방문 — 기록된다
        when(visitRepository.existsByVisitorIdAndHostIdAndVisitDate(1L, 2L, LocalDate.of(2026, 9, 6)))
                .thenReturn(false);
        var view = service.profile(1L, 2L);
        ArgumentCaptor<ProfileVisit> saved = ArgumentCaptor.forClass(ProfileVisit.class);
        verify(visitRepository).save(saved.capture());
        assertThat(saved.getValue().getVisitDate()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(view.visitCount()).isEqualTo(7L);
        assertThat(view.me()).isFalse();

        // 같은 날 재방문 — 기록하지 않는다
        when(visitRepository.existsByVisitorIdAndHostIdAndVisitDate(1L, 2L, LocalDate.of(2026, 9, 6)))
                .thenReturn(true);
        service.profile(1L, 2L);
        verify(visitRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("내 프로필 열람은 방문으로 기록하지 않는다")
    void ownProfileNotRecorded() {
        host();
        service.profile(2L, 2L);
        verify(visitRepository, never()).save(any());
    }

    @Test
    @DisplayName("방문자 목록 — 구독 회원이 아니면 SUBSCRIPTION_REQUIRED")
    void visitorsRequireSubscription() {
        when(subscriptionService.isActive(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.visitors(1L, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBSCRIPTION_REQUIRED);
    }

    @Test
    @DisplayName("좋아요 목록 — 글 주인이 아니면 FORBIDDEN, 주인이라도 구독이 없으면 SUBSCRIPTION_REQUIRED")
    void likersGates() {
        Post post = Post.builder().userId(2L).slug("s").title("글").bodyMd("...")
                .visibility(PostVisibility.PUBLIC).build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.likers(1L, 10L, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        when(subscriptionService.isActive(2L)).thenReturn(false);
        assertThatThrownBy(() -> service.likers(2L, 10L, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBSCRIPTION_REQUIRED);
        verify(likeRepository, never()).findAllByPostIdOrderByIdDesc(eq(10L), any());
    }
}
