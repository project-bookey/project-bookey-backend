package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.BlockedUserView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.social.UserBlock;
import app.bookey.domain.social.UserBlockRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 차단 계약 단위 테스트 — 한 방향, 다시 막아도 한 건, 자신·탈퇴한 사람은 막지 않음, 보내기 전 확인. */
class BlockServiceTest {

    private final UserBlockRepository blockRepository = mock(UserBlockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = mock(Clock.class);
    private final BlockService service = new BlockService(blockRepository, userRepository, clock);

    private static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("필드를 찾을 수 없습니다: " + field);
    }

    private static void assertApiError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private User user(long id) {
        User user = User.builder().handle("u" + id).email("u" + id + "@dev.local").nickname("상대").build();
        set(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("차단 — 처음이면 한 건 저장하고, 이미 막았으면 새로 저장하지 않는다")
    void blockIsIdempotent() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-05T03:00:00Z"));
        user(2L);
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.empty());
        when(blockRepository.save(any(UserBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockedUserView view = service.block(1L, 2L);

        assertThat(view.userId()).isEqualTo(2L);
        assertThat(view.nickname()).isEqualTo("상대");
        verify(blockRepository).save(any(UserBlock.class));

        UserBlock existing = UserBlock.of(1L, 2L);
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(existing));
        service.block(1L, 2L);
        verify(blockRepository).save(any(UserBlock.class));   // 여전히 한 번
    }

    @Test
    @DisplayName("차단 — 나 자신은 INVALID_REQUEST, 탈퇴한 사람은 NOT_FOUND")
    void blockGuards() {
        assertApiError(() -> service.block(1L, 1L), ErrorCode.INVALID_REQUEST);

        user(3L).changeStatus(UserStatus.TERMINATED);
        assertApiError(() -> service.block(1L, 3L), ErrorCode.NOT_FOUND);
        verify(blockRepository, never()).save(any());
    }

    @Test
    @DisplayName("풀기 — 막은 기록이 있으면 지우고, 없어도 그대로 성공")
    void unblock() {
        UserBlock existing = UserBlock.of(1L, 2L);
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(existing));
        service.unblock(1L, 2L);
        verify(blockRepository).delete(existing);

        when(blockRepository.findByBlockerIdAndBlockedId(1L, 4L)).thenReturn(Optional.empty());
        service.unblock(1L, 4L);
    }

    @Test
    @DisplayName("보내기 전 확인 — 내가 막았으면 USER_BLOCKED, 상대가 막았으면 USER_UNREACHABLE, 없으면 통과")
    void requireReachable() {
        when(blockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);
        assertApiError(() -> service.requireReachable(1L, 2L), ErrorCode.USER_BLOCKED);
        assertApiError(() -> service.requireReachable(2L, 1L), ErrorCode.USER_UNREACHABLE);
        assertThat(service.blockedEitherWay(2L, 1L)).isTrue();
        assertThat(service.hasBlocked(2L, 1L)).isFalse();

        service.requireReachable(1L, 3L);   // 아무도 막지 않았다
    }
}
