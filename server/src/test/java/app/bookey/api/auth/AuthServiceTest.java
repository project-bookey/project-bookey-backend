package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.EmailLoginRequest;
import app.bookey.api.auth.dto.AuthDtos.SocialLoginRequest;
import app.bookey.api.auth.dto.AuthDtos.TokenResponse;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.RefreshToken;
import app.bookey.domain.user.RefreshTokenRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Duration;
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

/**
 * 로그인 경로 단위 테스트 — 로컬·스모크가 쓰는 이메일 로그인과 소셜 provider 계약을 고정한다.
 * 저장소만 Mockito 로 대신하고 토큰 발급은 실제 {@link JwtTokenProvider} 를 쓴다(Spring 컨텍스트 없음).
 */
class AuthServiceTest {

    /** 평문 앞에 접두어만 붙이는 인코더 — 해시 비교 결과를 테스트에서 바로 읽을 수 있게 한다. */
    private static final PasswordEncoder PLAIN = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "enc:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return ("enc:" + raw).equals(encoded);
        }
    };

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new BookeyProperties(
            new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                    Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
            null, null, null, null));

    private AuthService service(List<SocialTokenVerifier> verifiers) {
        return new AuthService(userRepository, null, null, refreshTokenRepository, null,
                tokenProvider, null, null, verifiers, PLAIN);
    }

    private User user(long id, String email, String password) {
        User user = User.builder().handle("tester" + id).email(email).nickname("테스터").build();
        set(user, "id", id);
        if (password != null) {
            user.setPasswordHash(PLAIN.encode(password));
        }
        return user;
    }

    private void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertApiError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ───────────── 소셜 provider 계약 ─────────────

    @Test
    @DisplayName("소셜 provider 는 APPLE·GOOGLE·KAKAO 뿐이다 — 개발용 DEV 는 이메일 로그인으로 대체돼 없다")
    void socialProvidersHaveNoDev() {
        assertThat(AuthProvider.values())
                .containsExactly(AuthProvider.APPLE, AuthProvider.GOOGLE, AuthProvider.KAKAO);
        assertThatThrownBy(() -> AuthProvider.valueOf("DEV"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("검증기가 등록되지 않은 provider 로 소셜 로그인하면 INVALID_REQUEST('지원하지 않는 로그인 방식입니다.')")
    void socialLoginWithoutVerifierIsRejected() {
        AuthService service = service(List.of());

        assertThatThrownBy(() -> service.socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("지원하지 않는 로그인 방식입니다.")
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ───────────── 이메일 로그인 ─────────────

    @Test
    @DisplayName("이메일 로그인 — 이메일을 trim·소문자로 정규화해 찾고 USER_ACCESS 토큰(subject = 사용자 id)을 발급한다")
    void emailLoginIssuesTokens() {
        User user = user(7L, "tester1@dev.local", "password1234");
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local")).thenReturn(Optional.of(user));

        TokenResponse res = service(List.of())
                .emailLogin(new EmailLoginRequest("  Tester1@Dev.Local ", "password1234"));

        assertThat(res.newUser()).isFalse();
        assertThat(res.expiresInSec()).isEqualTo(3600L);
        assertThat(res.user().id()).isEqualTo(7L);
        assertThat(res.user().handle()).isEqualTo("tester7");

        Claims access = tokenProvider.parse(res.accessToken(), TokenType.USER_ACCESS);
        assertThat(tokenProvider.subjectId(access)).isEqualTo(7L);
        assertThat(tokenProvider.handle(access)).isEqualTo("tester7");
        Claims refresh = tokenProvider.parse(res.refreshToken(), TokenType.USER_REFRESH);
        assertThat(tokenProvider.subjectId(refresh)).isEqualTo(7L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("이메일 로그인 — 같은 초에 연속 로그인해도 저장되는 리프레시 토큰 해시가 서로 달라 token_hash 유니크 제약에 걸리지 않는다")
    void emailLoginTwiceInSameSecondStoresDistinctHashes() {
        User user = user(7L, "tester1@dev.local", "password1234");
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local")).thenReturn(Optional.of(user));
        AuthService service = service(List.of());
        EmailLoginRequest request = new EmailLoginRequest("tester1@dev.local", "password1234");

        // 세 번 연속이면 초 경계를 넘더라도 최소 두 번은 같은 초에 발급된다.
        TokenResponse first = service.emailLogin(request);
        TokenResponse second = service.emailLogin(request);
        TokenResponse third = service.emailLogin(request);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(RefreshToken::getUserId).containsOnly(7L);
        assertThat(saved.getAllValues()).extracting(RefreshToken::getTokenHash).doesNotHaveDuplicates();
        assertThat(List.of(first.refreshToken(), second.refreshToken(), third.refreshToken())).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이메일 로그인 — 없는 이메일은 INVALID_CREDENTIALS")
    void emailLoginUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@dev.local")).thenReturn(Optional.empty());

        assertApiError(() -> service(List.of())
                .emailLogin(new EmailLoginRequest("nobody@dev.local", "password1234")), ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일 로그인 — 비밀번호가 틀리면 INVALID_CREDENTIALS")
    void emailLoginWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local"))
                .thenReturn(Optional.of(user(1L, "tester1@dev.local", "password1234")));

        assertApiError(() -> service(List.of())
                .emailLogin(new EmailLoginRequest("tester1@dev.local", "wrong-password")), ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일 로그인 — 비밀번호가 없는(소셜 전용) 계정은 INVALID_CREDENTIALS")
    void emailLoginSocialOnlyAccount() {
        when(userRepository.findByEmailIgnoreCase("social@dev.local"))
                .thenReturn(Optional.of(user(2L, "social@dev.local", null)));

        assertApiError(() -> service(List.of())
                .emailLogin(new EmailLoginRequest("social@dev.local", "password1234")), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("이메일 로그인 — 정지·탈퇴 계정은 USER_SUSPENDED, 글쓰기 제한 계정은 로그인된다")
    void emailLoginRespectsUserStatus() {
        User suspended = user(3L, "suspended@dev.local", "password1234");
        suspended.changeStatus(UserStatus.SUSPENDED);
        User terminated = user(4L, "terminated@dev.local", "password1234");
        terminated.changeStatus(UserStatus.TERMINATED);
        User writeBanned = user(5L, "banned@dev.local", "password1234");
        writeBanned.changeStatus(UserStatus.WRITE_BANNED);
        when(userRepository.findByEmailIgnoreCase("suspended@dev.local")).thenReturn(Optional.of(suspended));
        when(userRepository.findByEmailIgnoreCase("terminated@dev.local")).thenReturn(Optional.of(terminated));
        when(userRepository.findByEmailIgnoreCase("banned@dev.local")).thenReturn(Optional.of(writeBanned));
        AuthService service = service(List.of());

        assertApiError(() -> service.emailLogin(new EmailLoginRequest("suspended@dev.local", "password1234")),
                ErrorCode.USER_SUSPENDED);
        assertApiError(() -> service.emailLogin(new EmailLoginRequest("terminated@dev.local", "password1234")),
                ErrorCode.USER_SUSPENDED);
        assertThat(service.emailLogin(new EmailLoginRequest("banned@dev.local", "password1234")).user().status())
                .isEqualTo("WRITE_BANNED");
    }
}
