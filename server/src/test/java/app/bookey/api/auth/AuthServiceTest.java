package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.EmailCodeRequest;
import app.bookey.api.auth.dto.AuthDtos.EmailCodeResponse;
import app.bookey.api.auth.dto.AuthDtos.EmailLoginRequest;
import app.bookey.api.auth.dto.AuthDtos.EmailSignupRequest;
import app.bookey.api.auth.dto.AuthDtos.SocialLoginRequest;
import app.bookey.api.auth.dto.AuthDtos.TokenResponse;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.EmailVerification;
import app.bookey.domain.user.EmailVerificationRepository;
import app.bookey.domain.user.RefreshToken;
import app.bookey.domain.user.RefreshTokenRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserIdentity;
import app.bookey.domain.user.UserIdentityRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
 * 로그인·가입 경로 단위 테스트 — 이메일 가입은 인증 코드를 요구하고, 소셜 로그인은 연동된 계정만 통과하는 계약을 고정한다.
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

    private static final BookeyProperties.Auth.Identity IDENTITY_STUB =
            new BookeyProperties.Auth.Identity("", "", "", true);
    /** 기본 테스트 모드는 EMAIL_CODE — 본인인증 모드는 identityService() 로 따로 만든다. */
    private static final BookeyProperties.Auth AUTH = new BookeyProperties.Auth(
            BookeyProperties.Auth.SignupVerification.EMAIL_CODE,
            new BookeyProperties.Auth.EmailCode(Duration.ofMinutes(10), Duration.ofMinutes(1), 5, true),
            IDENTITY_STUB);
    private static final BookeyProperties.Auth AUTH_IDENTITY = new BookeyProperties.Auth(
            BookeyProperties.Auth.SignupVerification.IDENTITY,
            new BookeyProperties.Auth.EmailCode(Duration.ofMinutes(10), Duration.ofMinutes(1), 5, true),
            IDENTITY_STUB);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserIdentityRepository identityRepository = mock(UserIdentityRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final EmailVerificationRepository emailVerificationRepository = mock(EmailVerificationRepository.class);
    private final EmailCodeSender emailCodeSender = mock(EmailCodeSender.class);
    private final IdentityVerifier identityVerifier = mock(IdentityVerifier.class);
    private final HandleGenerator handleGenerator = mock(HandleGenerator.class);
    private final app.bookey.domain.admin.OpsFlagRepository opsFlagRepository =
            mock(app.bookey.domain.admin.OpsFlagRepository.class);
    private final BookeyProperties properties = new BookeyProperties(
            new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                    Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
            AUTH, null, null, null, null, null, null, null);
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(properties);

    private AuthService service(List<SocialTokenVerifier> verifiers) {
        return new AuthService(userRepository, identityRepository, null, refreshTokenRepository,
                opsFlagRepository, emailVerificationRepository, tokenProvider, handleGenerator,
                properties, verifiers, PLAIN, emailCodeSender, identityVerifier);
    }

    /** IDENTITY 모드 서비스 — 가입이 휴대폰 본인인증을 요구한다. */
    private AuthService identityService() {
        BookeyProperties identityProps = new BookeyProperties(
                new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                        Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
                AUTH_IDENTITY, null, null, null, null, null, null, null);
        return new AuthService(userRepository, identityRepository, null, refreshTokenRepository,
                opsFlagRepository, emailVerificationRepository,
                new JwtTokenProvider(identityProps), handleGenerator,
                identityProps, List.of(), PLAIN, emailCodeSender, identityVerifier);
    }

    private User user(long id, String email, String password) {
        User user = User.builder().handle("tester" + id).email(email).nickname("테스터").build();
        set(user, "id", id);
        if (password != null) {
            user.setPasswordHash(PLAIN.encode(password));
        }
        return user;
    }

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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private EmailVerification verification(String email, String code, Instant expiresAt) {
        return new EmailVerification(email, sha256(code), expiresAt);
    }

    private static void assertApiError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    /** 가입 성공 경로 공통 — save 가 id 를 채워 돌려주게 한다. */
    private void stubUserSave() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            set(saved, "id", 42L);
            return saved;
        });
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

        assertThatThrownBy(() -> service.socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token")))
                .isInstanceOf(ApiException.class)
                .hasMessage("지원하지 않는 로그인 방식입니다.")
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ───────────── 소셜 로그인 = 연동 계정 전용 ─────────────

    private SocialTokenVerifier kakaoVerifier(String uid) {
        return new SocialTokenVerifier() {
            @Override
            public AuthProvider provider() {
                return AuthProvider.KAKAO;
            }

            @Override
            public SocialProfile verify(String token) {
                return new SocialProfile(AuthProvider.KAKAO, uid, null, null, null);
            }
        };
    }

    @Test
    @DisplayName("소셜 로그인 — 연동되지 않은 소셜 계정은 SOCIAL_SIGNUP_DISABLED (이메일 가입 후 연동 유도, 자동 가입 없음)")
    void socialLoginUnlinkedIdentityIsRejected() {
        when(identityRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.empty());

        assertApiError(() -> service(List.of(kakaoVerifier("kakao-1")))
                .socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token")), ErrorCode.SOCIAL_SIGNUP_DISABLED);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("소셜 로그인 — 이미 연동된 계정은 newUser=false 로 로그인된다")
    void socialLoginLinkedIdentitySucceeds() {
        User user = user(9L, "linked@dev.local", "password1234");
        when(identityRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-9"))
                .thenReturn(Optional.of(new UserIdentity(9L, AuthProvider.KAKAO, "kakao-9")));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        TokenResponse res = service(List.of(kakaoVerifier("kakao-9")))
                .socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token"));

        assertThat(res.newUser()).isFalse();
        assertThat(res.user().id()).isEqualTo(9L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ───────────── 가입 인증 코드 발급 ─────────────

    @Test
    @DisplayName("코드 발급 — 6자리 코드를 해시로 저장하고 발송한다. expose=true 면 응답에 devCode 가 동봉된다")
    void requestEmailCodeIssuesAndSends() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.empty());

        EmailCodeResponse res = service(List.of())
                .requestEmailCode(new EmailCodeRequest("  New@Dev.Local "));

        assertThat(res.expiresInSec()).isEqualTo(600L);
        assertThat(res.devCode()).hasSize(6).containsOnlyDigits();

        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@dev.local");
        assertThat(saved.getValue().getCodeHash()).isEqualTo(sha256(res.devCode()));
        verify(emailCodeSender).send("new@dev.local", res.devCode(), Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("코드 발급 — 이미 가입된 이메일은 EMAIL_ALREADY_EXISTS")
    void requestEmailCodeForRegisteredEmail() {
        when(userRepository.existsByEmailIgnoreCase("tester1@dev.local")).thenReturn(true);

        assertApiError(() -> service(List.of())
                .requestEmailCode(new EmailCodeRequest("tester1@dev.local")), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(emailVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("코드 발급 — 쿨다운(1분) 안의 재요청은 RATE_LIMITED")
    void requestEmailCodeWithinCooldown() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification latest = verification("new@dev.local", "123456", Instant.now().plus(Duration.ofMinutes(10)));
        set(latest, "createdAt", Instant.now());
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(latest));

        assertApiError(() -> service(List.of())
                .requestEmailCode(new EmailCodeRequest("new@dev.local")), ErrorCode.RATE_LIMITED);
        verify(emailVerificationRepository, never()).save(any());
    }

    // ───────────── 이메일 가입 (인증 코드 필수) ─────────────

    private EmailSignupRequest signupRequest(String code) {
        return new EmailSignupRequest("new@dev.local", "password1234", "새 독서가", code, null);
    }

    @Test
    @DisplayName("가입 — 올바른 코드면 코드를 소진하고 email_verified_at 을 채워 가입시킨다")
    void emailSignupWithValidCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(handleGenerator.generate("new")).thenReturn("newbie");
        stubUserSave();
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(verification));

        TokenResponse res = service(List.of()).emailSignup(signupRequest("123456"));

        assertThat(res.newUser()).isTrue();
        assertThat(res.user().handle()).isEqualTo("newbie");
        assertThat(verification.isConsumed()).isTrue();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailVerifiedAt()).isNotNull();
        Claims access = tokenProvider.parse(res.accessToken(), TokenType.USER_ACCESS);
        assertThat(tokenProvider.subjectId(access)).isEqualTo(42L);
    }

    @Test
    @DisplayName("가입 — 코드가 틀리면 EMAIL_CODE_INVALID, 실패 횟수가 누적된다")
    void emailSignupWithWrongCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(verification));

        assertApiError(() -> service(List.of()).emailSignup(signupRequest("999999")), ErrorCode.EMAIL_CODE_INVALID);
        assertThat(verification.getAttemptCount()).isEqualTo((short) 1);
        assertThat(verification.isConsumed()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 — 코드를 발급받은 적이 없으면 EMAIL_CODE_INVALID")
    void emailSignupWithoutIssuedCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.empty());

        assertApiError(() -> service(List.of()).emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_INVALID);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 — 만료·소진·시도 초과 코드는 EMAIL_CODE_EXPIRED (재발급 유도)")
    void emailSignupWithUnusableCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        AuthService service = service(List.of());

        // 만료
        EmailVerification expired = verification("new@dev.local", "123456", Instant.now().minusSeconds(1));
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(expired));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        // 이미 소진
        EmailVerification consumed = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        consumed.consume(Instant.now());
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(consumed));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        // 시도 초과 — 올바른 코드라도 무효
        EmailVerification tried = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        for (int i = 0; i < 5; i++) {
            tried.recordFailedAttempt();
        }
        when(emailVerificationRepository.findTopByEmailOrderByIdDesc("new@dev.local"))
                .thenReturn(Optional.of(tried));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 — 이미 가입된 이메일은 코드 검증 전에 EMAIL_ALREADY_EXISTS")
    void emailSignupDuplicatedEmail() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(true);

        assertApiError(() -> service(List.of()).emailSignup(signupRequest("123456")), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(emailVerificationRepository, never()).findTopByEmailOrderByIdDesc(any());
    }

    // ───────────── 휴대폰 본인인증 가입 (IDENTITY 모드) ─────────────

    @Test
    @DisplayName("본인인증 가입 — 인증 결과를 계정에 기록하고 email_verified_at 대신 identity_verified_at 을 채운다")
    void identitySignupRecordsIdentity() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(handleGenerator.generate("new")).thenReturn("newbie");
        stubUserSave();
        when(identityVerifier.verify("dev-abc")).thenReturn(new app.bookey.api.auth.VerifiedIdentity(
                "홍길동", "01012341234", java.time.LocalDate.of(1995, 1, 1), "ci-1", "di-1"));
        when(userRepository.existsByCi("ci-1")).thenReturn(false);

        TokenResponse res = identityService().emailSignup(new EmailSignupRequest(
                "new@dev.local", "password1234", "새 독서가", null, "dev-abc"));

        assertThat(res.newUser()).isTrue();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getCi()).isEqualTo("ci-1");
        assertThat(saved.getValue().getRealName()).isEqualTo("홍길동");
        assertThat(saved.getValue().getIdentityVerifiedAt()).isNotNull();
        assertThat(saved.getValue().getEmailVerifiedAt()).isNull();
        verify(emailVerificationRepository, never()).findTopByEmailOrderByIdDesc(any());
    }

    @Test
    @DisplayName("본인인증 가입 — id 가 없으면 IDENTITY_VERIFICATION_REQUIRED, 같은 CI 는 IDENTITY_ALREADY_REGISTERED")
    void identitySignupGuards() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        AuthService service = identityService();

        assertApiError(() -> service.emailSignup(new EmailSignupRequest(
                "new@dev.local", "password1234", "새 독서가", null, null)),
                ErrorCode.IDENTITY_VERIFICATION_REQUIRED);

        when(identityVerifier.verify("dev-abc")).thenReturn(new app.bookey.api.auth.VerifiedIdentity(
                "홍길동", "01012341234", null, "ci-1", "di-1"));
        when(userRepository.existsByCi("ci-1")).thenReturn(true);
        assertApiError(() -> service.emailSignup(new EmailSignupRequest(
                "new@dev.local", "password1234", "새 독서가", null, "dev-abc")),
                ErrorCode.IDENTITY_ALREADY_REGISTERED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("EMAIL_CODE 모드 가입 — 코드가 아예 없으면 EMAIL_CODE_INVALID")
    void emailSignupWithoutCodeField() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);

        assertApiError(() -> service(List.of()).emailSignup(new EmailSignupRequest(
                "new@dev.local", "password1234", "새 독서가", null, null)), ErrorCode.EMAIL_CODE_INVALID);
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
