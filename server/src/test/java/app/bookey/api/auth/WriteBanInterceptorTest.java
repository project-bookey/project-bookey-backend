package app.bookey.api.auth;

import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthUser;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WriteBanInterceptorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WriteBanInterceptor interceptor = new WriteBanInterceptor(new UserWriteGuard(userRepository));

    static class Endpoints {
        @WriteBanGuarded
        public void write() {
        }

        public void read() {
        }
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
    }

    private static void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUser(userId, "reader"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("쓰기정지 회원은 @WriteBanGuarded 엔드포인트에서 WRITE_BANNED 로 막힌다")
    void writeBannedBlocked() throws Exception {
        loginAs(10L);
        when(userRepository.findStatusById(10L)).thenReturn(Optional.of(UserStatus.WRITE_BANNED));

        assertThatThrownBy(() -> interceptor.preHandle(null, null, handler("write")))
                .extracting("errorCode").isEqualTo(ErrorCode.WRITE_BANNED);
    }

    @Test
    @DisplayName("정상 회원은 통과하고, 표시가 없는 엔드포인트는 상태를 읽지도 않는다")
    void activeAndUnguardedPass() throws Exception {
        loginAs(10L);
        when(userRepository.findStatusById(10L)).thenReturn(Optional.of(UserStatus.ACTIVE));
        assertThat(interceptor.preHandle(null, null, handler("write"))).isTrue();

        loginAs(11L);
        assertThat(interceptor.preHandle(null, null, handler("read"))).isTrue();
        verify(userRepository, never()).findStatusById(11L);
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 인증 단계에 맡기고 건너뛴다")
    void anonymousSkipped() throws Exception {
        assertThat(interceptor.preHandle(null, null, handler("write"))).isTrue();
        verify(userRepository, never()).findStatusById(any());
    }
}
