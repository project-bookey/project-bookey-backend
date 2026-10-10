package app.bookey.api.auth;

import app.bookey.common.security.UserAccessRevocations;
import app.bookey.api.auth.dto.AuthDtos.EmailCodeRequest;
import app.bookey.api.auth.dto.AuthDtos.EmailCodeResponse;
import app.bookey.api.auth.dto.AuthDtos.EmailCodeVerifyRequest;
import app.bookey.api.auth.dto.AuthDtos.EmailLoginRequest;
import app.bookey.api.auth.dto.AuthDtos.EmailSignupRequest;
import app.bookey.api.auth.dto.AuthDtos.MeResponse;
import app.bookey.api.auth.dto.AuthDtos.PasswordResetRequest;
import app.bookey.api.auth.dto.AuthDtos.SignupConsent;
import app.bookey.api.auth.dto.AuthDtos.SocialLoginRequest;
import app.bookey.api.legal.ConsentService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.auth.dto.AuthDtos.TokenResponse;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.legal.LegalDocument;
import app.bookey.domain.legal.UserConsent;
import app.bookey.domain.legal.UserConsentRepository;
import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.EmailCodePurpose;
import app.bookey.domain.user.EmailVerification;
import app.bookey.domain.user.EmailVerificationRepository;
import app.bookey.domain.user.DeletedEmailHashRepository;
import app.bookey.domain.user.RefreshToken;
import app.bookey.domain.user.RefreshTokenRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserIdentity;
import app.bookey.domain.user.UserIdentityRepository;
import app.bookey.domain.user.UserDeviceRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인·가입 경로 단위 테스트 — 이메일 가입은 인증 코드를 요구하고, 소셜 로그인은 가입 동의가 오면 미가입 계정을 만든다.
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
            new BookeyProperties.Auth.EmailCode(Duration.ofMinutes(10), 10, Duration.ofMinutes(30), 5, true),
            IDENTITY_STUB);
    private static final BookeyProperties.Auth AUTH_IDENTITY = new BookeyProperties.Auth(
            BookeyProperties.Auth.SignupVerification.IDENTITY,
            new BookeyProperties.Auth.EmailCode(Duration.ofMinutes(10), 10, Duration.ofMinutes(30), 5, true),
            IDENTITY_STUB);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserIdentityRepository identityRepository = mock(UserIdentityRepository.class);
    private final UserDeviceRepository deviceRepository = mock(UserDeviceRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final EmailVerificationRepository emailVerificationRepository = mock(EmailVerificationRepository.class);
    private final EmailCodeSender emailCodeSender = mock(EmailCodeSender.class);
    private final IdentityVerifier identityVerifier = mock(IdentityVerifier.class);
    private final InquiryRepository inquiryRepository = mock(InquiryRepository.class);
    private final DeletedEmailHashRepository deletedEmailHashRepository = mock(DeletedEmailHashRepository.class);
    private final UserConsentRepository consentRepository = mock(UserConsentRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    /** 동의 검증은 실제 규칙으로 — 저장소·알림만 가짜. */
    private final ConsentService consentService = new ConsentService(consentRepository, userRepository, notificationService);
    private final AccountEraser accountEraser = mock(AccountEraser.class);
    private final HandleGenerator handleGenerator = mock(HandleGenerator.class);
    private final app.bookey.domain.admin.OpsFlagRepository opsFlagRepository =
            mock(app.bookey.domain.admin.OpsFlagRepository.class);
    private final BookeyProperties properties = new BookeyProperties(
            new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                    Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
            AUTH, null, null, null, null, null, null, null, null);
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(properties);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final UserAccessRevocations accessRevocations = mock(UserAccessRevocations.class);

    /** 코드 발급 상한은 따로 시험한다 — 나머지 시험에서는 늘 통과시킨다. */
    @BeforeEach
    void allowEmailCodes() {
        when(rateLimiter.acquire(anyString(), anyInt(), any())).thenReturn(new RateLimiter.Permit(true, 9));
    }

    private AuthService service(List<SocialTokenVerifier> verifiers) {
        return new AuthService(userRepository, identityRepository, deviceRepository, refreshTokenRepository,
                opsFlagRepository, emailVerificationRepository, tokenProvider, handleGenerator,
                properties, verifiers, PLAIN, emailCodeSender, identityVerifier, inquiryRepository,
                deletedEmailHashRepository, consentService, accountEraser, rateLimiter, accessRevocations);
    }

    /** IDENTITY 모드 서비스 — 가입이 휴대폰 본인인증을 요구한다. */
    private AuthService identityService() {
        BookeyProperties identityProps = new BookeyProperties(
                new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                        Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
                AUTH_IDENTITY, null, null, null, null, null, null, null, null);
        return new AuthService(userRepository, identityRepository, deviceRepository, refreshTokenRepository,
                opsFlagRepository, emailVerificationRepository,
                new JwtTokenProvider(identityProps), handleGenerator,
                identityProps, List.of(), PLAIN, emailCodeSender, identityVerifier, inquiryRepository,
                deletedEmailHashRepository, consentService, accountEraser, rateLimiter, accessRevocations);
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
        return verification(email, EmailCodePurpose.SIGNUP, code, expiresAt);
    }

    private EmailVerification verification(String email, EmailCodePurpose purpose, String code, Instant expiresAt) {
        return new EmailVerification(email, purpose, sha256(code), expiresAt);
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

        assertThatThrownBy(() -> service.socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("지원하지 않는 로그인 방식이에요.")
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ───────────── 소셜 로그인 ─────────────

    private SocialTokenVerifier kakaoVerifier(String uid) {
        return kakaoVerifier(uid, null, null);
    }

    private SocialTokenVerifier kakaoVerifier(String uid, String email, String nickname) {
        return new SocialTokenVerifier() {
            @Override
            public AuthProvider provider() {
                return AuthProvider.KAKAO;
            }

            @Override
            public SocialProfile verify(String token) {
                return new SocialProfile(AuthProvider.KAKAO, uid, email, nickname, "https://img.example/avatar.png");
            }
        };
    }

    @Test
    @DisplayName("소셜 로그인 — 연동되지 않은 소셜 계정은 가입 동의와 함께 오면 신규 가입되고 newUser=true 로 토큰이 발급된다")
    void socialLoginUnlinkedIdentityCreatesUser() {
        when(identityRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("social@dev.local")).thenReturn(false);
        when(handleGenerator.generate("social")).thenReturn("social");
        stubUserSave();

        TokenResponse res = service(List.of(kakaoVerifier("kakao-1", "Social@Dev.Local", "소셜")))
                .socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", requiredConsent()));

        assertThat(res.newUser()).isTrue();
        assertThat(savedConsentKinds()).containsExactly(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.AGE_14);
        assertThat(res.user().id()).isEqualTo(42L);
        assertThat(res.user().handle()).isEqualTo("social");
        assertThat(res.user().email()).isEqualTo("social@dev.local");

        ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityRepository).save(identity.capture());
        assertThat(identity.getValue().getUserId()).isEqualTo(42L);
        assertThat(identity.getValue().getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(identity.getValue().getProviderUid()).isEqualTo("kakao-1");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("소셜 로그인 — 처음 보는 소셜 계정인데 가입 동의가 없으면 계정을 만들지 않고 LEGAL_CONSENT_REQUIRED")
    void socialSignupWithoutConsentCreatesNothing() {
        when(identityRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("social@dev.local")).thenReturn(false);
        AuthService service = service(List.of(kakaoVerifier("kakao-1", "social@dev.local", "소셜")));

        assertApiError(() -> service.socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", null)),
                ErrorCode.LEGAL_CONSENT_REQUIRED);
        SignupConsent noAge = new SignupConsent(true, LegalDocument.TERMS.getVersion(),
                true, LegalDocument.PRIVACY_CONSENT.getVersion(), false, false, null);
        assertApiError(() -> service.socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", noAge)),
                ErrorCode.LEGAL_CONSENT_REQUIRED);
        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any(UserIdentity.class));
        verify(consentRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("소셜 로그인 — provider 이메일이 기존 계정과 같으면 자동 병합하지 않고 EMAIL_ALREADY_EXISTS")
    void socialLoginUnlinkedIdentityWithExistingEmailIsRejected() {
        when(identityRepository.findByProviderAndProviderUid(AuthProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("linked@dev.local")).thenReturn(true);

        assertApiError(() -> service(List.of(kakaoVerifier("kakao-1", "linked@dev.local", "소셜")))
                .socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", null)), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any(UserIdentity.class));
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
                .socialLogin(new SocialLoginRequest(AuthProvider.KAKAO, "token", null));

        assertThat(res.newUser()).isFalse();
        assertThat(res.user().id()).isEqualTo(9L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ───────────── 가입 인증 코드 발급 ─────────────

    @Test
    @DisplayName("코드 발급 — 6자리 코드를 해시로 저장하고 발송한다. expose=true 면 응답에 devCode 가 동봉된다")
    void requestEmailCodeIssuesAndSends() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.empty());

        EmailCodeResponse res = service(List.of())
                .requestEmailCode(new EmailCodeRequest("  New@Dev.Local "));

        assertThat(res.expiresInSec()).isEqualTo(600L);
        assertThat(res.devCode()).hasSize(6).containsOnlyDigits();
        assertThat(res.resendsLeft()).isEqualTo(9);
        assertThat(res.sendLimit()).isEqualTo(10);

        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@dev.local");
        assertThat(saved.getValue().getCodeHash()).isEqualTo(sha256(res.devCode()));
        verify(emailCodeSender).send("new@dev.local", res.devCode(), Duration.ofMinutes(10), EmailCodePurpose.SIGNUP);
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
    @DisplayName("코드 발급 — 방금 받았어도 기다림 없이 바로 새 코드를 다시 받는다")
    void requestEmailCodeAgainRightAway() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification latest = verification("new@dev.local", "123456", Instant.now().plus(Duration.ofMinutes(10)));
        set(latest, "createdAt", Instant.now());
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(latest));

        service(List.of()).requestEmailCode(new EmailCodeRequest("new@dev.local"));

        verify(emailVerificationRepository).save(any());
        verify(rateLimiter).acquire("email-code:SIGNUP:new@dev.local", 10, Duration.ofHours(1));
    }

    @Test
    @DisplayName("코드 발급 — 같은 이메일로 1시간 상한을 넘기면 RATE_LIMITED, 발송하지 않는다")
    void requestEmailCodeOverHourlyLimit() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(rateLimiter.acquire("email-code:SIGNUP:new@dev.local", 10, Duration.ofHours(1)))
                .thenReturn(new RateLimiter.Permit(false, 0));

        assertApiError(() -> service(List.of())
                .requestEmailCode(new EmailCodeRequest("new@dev.local")), ErrorCode.RATE_LIMITED);
        verify(emailVerificationRepository, never()).save(any());
        verify(emailCodeSender, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("코드 사전 확인 — 올바른 코드는 가입 전까지 소진하지 않는다")
    void verifySignupEmailCodeDoesNotConsumeCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(verification));

        service(List.of()).verifySignupEmailCode(new EmailCodeVerifyRequest("NEW@dev.local", "123456"));

        assertThat(verification.isConsumed()).isFalse();
        assertThat(verification.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("코드 사전 확인 — 맞은 코드는 가입을 마칠 시간(30분)만큼 유효 시각을 늘린다")
    void verifySignupEmailCodeHoldsCodeForSignup() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(1)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(verification));

        service(List.of()).verifySignupEmailCode(new EmailCodeVerifyRequest("new@dev.local", "123456"));

        assertThat(verification.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(29)));
        verify(emailVerificationRepository).save(verification);
        // 늘린 뒤에는 처음 유효 시각이 지나도 가입에서 쓸 수 있다.
        assertThat(verification.isExpired(Instant.now().plus(Duration.ofMinutes(5)))).isFalse();
    }

    @Test
    @DisplayName("코드 사전 확인 — 틀린 코드는 실패 횟수를 누적한다")
    void verifySignupEmailCodeRejectsWrongCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(verification));

        assertApiError(() -> service(List.of())
                .verifySignupEmailCode(new EmailCodeVerifyRequest("new@dev.local", "999999")),
                ErrorCode.EMAIL_CODE_INVALID);

        assertThat(verification.getAttemptCount()).isEqualTo((short) 1);
        verify(emailVerificationRepository).save(verification);
    }

    // ───────────── 이메일 가입 (인증 코드 필수) ─────────────

    private EmailSignupRequest signupRequest(String code) {
        return agreedSignupRequest(code, null);
    }

    private EmailSignupRequest agreedSignupRequest(String code, String identityVerificationId) {
        return new EmailSignupRequest("new@dev.local", "password1234", "새 독서가", code,
                identityVerificationId, requiredConsent());
    }

    /** 필수 셋에 지금 버전으로 동의하고 광고 수신은 고르지 않은 가입 동의. */
    private static SignupConsent requiredConsent() {
        return new SignupConsent(true, LegalDocument.TERMS.getVersion(),
                true, LegalDocument.PRIVACY_CONSENT.getVersion(), true, false, null);
    }

    @Test
    @DisplayName("가입 — 올바른 코드면 코드를 소진하고 email_verified_at 을 채워 가입시킨다")
    void emailSignupWithValidCode() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(handleGenerator.generate("new")).thenReturn("newbie");
        stubUserSave();
        EmailVerification verification = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
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
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
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
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
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
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(expired));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        // 이미 소진
        EmailVerification consumed = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        consumed.consume(Instant.now());
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(consumed));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        // 시도 초과 — 올바른 코드라도 무효
        EmailVerification tried = verification("new@dev.local", "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        for (int i = 0; i < 5; i++) {
            tried.recordFailedAttempt();
        }
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(tried));
        assertApiError(() -> service.emailSignup(signupRequest("123456")), ErrorCode.EMAIL_CODE_EXPIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 — 이미 가입된 이메일은 코드 검증 전에 EMAIL_ALREADY_EXISTS")
    void emailSignupDuplicatedEmail() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(true);

        assertApiError(() -> service(List.of()).emailSignup(signupRequest("123456")), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(emailVerificationRepository, never()).findTopByEmailAndPurposeOrderByIdDesc(any(), any());
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

        TokenResponse res = identityService().emailSignup(agreedSignupRequest(null, "dev-abc"));

        assertThat(res.newUser()).isTrue();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getCi()).isEqualTo("ci-1");
        assertThat(saved.getValue().getRealName()).isEqualTo("홍길동");
        assertThat(saved.getValue().getIdentityVerifiedAt()).isNotNull();
        assertThat(saved.getValue().getEmailVerifiedAt()).isNull();
        assertThat(savedConsentKinds()).containsExactly(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.AGE_14);
        verify(emailVerificationRepository, never()).findTopByEmailAndPurposeOrderByIdDesc(any(), any());
    }

    @Test
    @DisplayName("본인인증 가입 — id 가 없으면 IDENTITY_VERIFICATION_REQUIRED, 같은 CI 는 IDENTITY_ALREADY_REGISTERED")
    void identitySignupGuards() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        AuthService service = identityService();

        assertApiError(() -> service.emailSignup(agreedSignupRequest(null, null)),
                ErrorCode.IDENTITY_VERIFICATION_REQUIRED);

        when(identityVerifier.verify("dev-abc")).thenReturn(new app.bookey.api.auth.VerifiedIdentity(
                "홍길동", "01012341234", null, "ci-1", "di-1"));
        when(userRepository.existsByCi("ci-1")).thenReturn(true);
        assertApiError(() -> service.emailSignup(agreedSignupRequest(null, "dev-abc")),
                ErrorCode.IDENTITY_ALREADY_REGISTERED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("EMAIL_CODE 모드 가입 — 코드가 아예 없으면 EMAIL_CODE_INVALID")
    void emailSignupWithoutCodeField() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);

        assertApiError(() -> service(List.of()).emailSignup(agreedSignupRequest(null, null)),
                ErrorCode.EMAIL_CODE_INVALID);
    }

    @Test
    @DisplayName("가입 — 필수 동의 누락·구버전·만 14세 미확인, 광고 수신 문서 구버전이면 LEGAL_CONSENT_REQUIRED")
    void emailSignupRequiresCurrentLegalConsent() {
        String terms = LegalDocument.TERMS.getVersion();
        String privacy = LegalDocument.PRIVACY_CONSENT.getVersion();
        List<SignupConsent> rejected = List.of(
                new SignupConsent(false, terms, true, privacy, true, false, null),
                new SignupConsent(true, "2025-01-01", true, privacy, true, false, null),
                new SignupConsent(true, terms, true, privacy, null, false, null),
                new SignupConsent(true, terms, true, privacy, false, false, null),
                new SignupConsent(true, terms, true, privacy, true, true, "2025-01-01"));
        for (SignupConsent consent : rejected) {
            EmailSignupRequest request = new EmailSignupRequest(
                    "new@dev.local", "password1234", "새 독서가", "123456", null, consent);
            assertApiError(() -> service(List.of()).emailSignup(request), ErrorCode.LEGAL_CONSENT_REQUIRED);
        }
        verify(userRepository, never()).save(any());
        verify(consentRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 — 광고 수신까지 동의하면 네 가지 동의를 같은 시각으로 남기고 처리 결과 알림을 보낸다")
    void emailSignupRecordsMarketingConsentAndNotifies() {
        when(userRepository.existsByEmailIgnoreCase("new@dev.local")).thenReturn(false);
        when(handleGenerator.generate("new")).thenReturn("newbie");
        stubUserSave();
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc("new@dev.local", EmailCodePurpose.SIGNUP))
                .thenReturn(Optional.of(verification("new@dev.local", "123456",
                        Instant.now().plus(Duration.ofMinutes(10)))));
        SignupConsent consent = new SignupConsent(true, LegalDocument.TERMS.getVersion(),
                true, LegalDocument.PRIVACY_CONSENT.getVersion(), true, true, LegalDocument.MARKETING.getVersion());

        service(List.of()).emailSignup(new EmailSignupRequest(
                "new@dev.local", "password1234", "새 독서가", "123456", null, consent));

        ArgumentCaptor<UserConsent> saved = ArgumentCaptor.forClass(UserConsent.class);
        verify(consentRepository, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(UserConsent::getKind)
                .containsExactly(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.AGE_14, ConsentKind.MARKETING);
        assertThat(saved.getAllValues()).allMatch(c -> c.isAgreed() && c.getUserId().equals(42L));
        assertThat(saved.getAllValues()).extracting(UserConsent::getCreatedAt).containsOnly(saved.getValue().getCreatedAt());
        assertThat(saved.getAllValues().get(3).getVersion()).isEqualTo(LegalDocument.MARKETING.getVersion());
        verify(notificationService).inApp(any());
    }

    /** 저장된 동의 행의 종류(저장 순서대로). */
    private List<ConsentKind> savedConsentKinds() {
        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(consentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(UserConsent::getKind).toList();
    }

    // ───────────── 비밀번호 재설정 ─────────────

    @Test
    @DisplayName("재설정 코드 발급 — 가입된 이메일이면 PASSWORD_RESET 용도로 해시를 저장하고 그 용도로 발송한다")
    void requestPasswordResetCodeIssuesAndSends() {
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local"))
                .thenReturn(Optional.of(user(7L, "tester1@dev.local", "password1234")));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(
                "tester1@dev.local", EmailCodePurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        EmailCodeResponse res = service(List.of())
                .requestPasswordResetCode(new EmailCodeRequest("  Tester1@Dev.Local "));

        assertThat(res.expiresInSec()).isEqualTo(600L);
        assertThat(res.devCode()).hasSize(6).containsOnlyDigits();
        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("tester1@dev.local");
        assertThat(saved.getValue().getPurpose()).isEqualTo(EmailCodePurpose.PASSWORD_RESET);
        assertThat(saved.getValue().getCodeHash()).isEqualTo(sha256(res.devCode()));
        verify(emailCodeSender).send("tester1@dev.local", res.devCode(), Duration.ofMinutes(10),
                EmailCodePurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("재설정 코드 발급 — 가입되지 않은 이메일은 EMAIL_NOT_REGISTERED, 정지 계정은 USER_SUSPENDED. 둘 다 발송하지 않는다")
    void requestPasswordResetCodeGuards() {
        when(userRepository.findByEmailIgnoreCase("nobody@dev.local")).thenReturn(Optional.empty());
        User suspended = user(3L, "suspended@dev.local", "password1234");
        suspended.changeStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase("suspended@dev.local")).thenReturn(Optional.of(suspended));
        AuthService service = service(List.of());

        assertApiError(() -> service.requestPasswordResetCode(new EmailCodeRequest("nobody@dev.local")),
                ErrorCode.EMAIL_NOT_REGISTERED);
        assertApiError(() -> service.requestPasswordResetCode(new EmailCodeRequest("suspended@dev.local")),
                ErrorCode.USER_SUSPENDED);
        verify(emailVerificationRepository, never()).save(any());
        verify(emailCodeSender, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("재설정 코드 발급 — 가입 코드와 상한을 따로 세고, 1시간 상한을 넘기면 RATE_LIMITED")
    void requestPasswordResetCodeOverHourlyLimit() {
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local"))
                .thenReturn(Optional.of(user(7L, "tester1@dev.local", "password1234")));
        when(rateLimiter.acquire("email-code:PASSWORD_RESET:tester1@dev.local", 10, Duration.ofHours(1)))
                .thenReturn(new RateLimiter.Permit(false, 0));

        assertApiError(() -> service(List.of())
                .requestPasswordResetCode(new EmailCodeRequest("tester1@dev.local")), ErrorCode.RATE_LIMITED);
        verify(emailVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("재설정 — 올바른 코드면 코드를 소진하고 비밀번호를 바꾼 뒤, 기존 리프레시 토큰을 모두 폐기하고 로그인시킨다")
    void resetPasswordWithValidCode() {
        User user = user(7L, "tester1@dev.local", "old-password");
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local")).thenReturn(Optional.of(user));
        EmailVerification code = verification("tester1@dev.local", EmailCodePurpose.PASSWORD_RESET, "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(
                "tester1@dev.local", EmailCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));

        TokenResponse res = service(List.of())
                .resetPassword(new PasswordResetRequest(" Tester1@Dev.Local", "123456", "new-password"));

        assertThat(code.isConsumed()).isTrue();
        assertThat(PLAIN.matches("new-password", user.getPasswordHash())).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllByUserId(any(), any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(res.newUser()).isFalse();
        assertThat(res.user().id()).isEqualTo(7L);
        assertThat(res.user().hasPassword()).isTrue();
    }

    @Test
    @DisplayName("재설정 — 코드가 틀리면 EMAIL_CODE_INVALID, 실패 횟수만 쌓이고 비밀번호·토큰은 그대로다")
    void resetPasswordWithWrongCode() {
        User user = user(7L, "tester1@dev.local", "old-password");
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local")).thenReturn(Optional.of(user));
        EmailVerification code = verification("tester1@dev.local", EmailCodePurpose.PASSWORD_RESET, "123456",
                Instant.now().plus(Duration.ofMinutes(10)));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(
                "tester1@dev.local", EmailCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));

        assertApiError(() -> service(List.of())
                .resetPassword(new PasswordResetRequest("tester1@dev.local", "999999", "new-password")),
                ErrorCode.EMAIL_CODE_INVALID);
        assertThat(code.getAttemptCount()).isEqualTo((short) 1);
        assertThat(PLAIN.matches("old-password", user.getPasswordHash())).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("재설정 — 재설정 코드를 받은 적이 없으면(가입 코드만 있어도) EMAIL_CODE_INVALID")
    void resetPasswordLooksOnlyAtResetCodes() {
        when(userRepository.findByEmailIgnoreCase("tester1@dev.local"))
                .thenReturn(Optional.of(user(7L, "tester1@dev.local", "old-password")));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(
                "tester1@dev.local", EmailCodePurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        assertApiError(() -> service(List.of())
                .resetPassword(new PasswordResetRequest("tester1@dev.local", "123456", "new-password")),
                ErrorCode.EMAIL_CODE_INVALID);
        verify(emailVerificationRepository, never())
                .findTopByEmailAndPurposeOrderByIdDesc(any(), org.mockito.ArgumentMatchers.eq(EmailCodePurpose.SIGNUP));
    }

    @Test
    @DisplayName("재설정 — 소셜 전용 계정도 이메일 코드로 비밀번호를 만들 수 있다(hasPassword=true)")
    void resetPasswordGivesSocialOnlyAccountAPassword() {
        User social = user(2L, "social@dev.local", null);
        when(userRepository.findByEmailIgnoreCase("social@dev.local")).thenReturn(Optional.of(social));
        when(emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(
                "social@dev.local", EmailCodePurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(verification("social@dev.local", EmailCodePurpose.PASSWORD_RESET, "123456",
                        Instant.now().plus(Duration.ofMinutes(10)))));

        TokenResponse res = service(List.of())
                .resetPassword(new PasswordResetRequest("social@dev.local", "123456", "new-password"));

        assertThat(res.user().hasPassword()).isTrue();
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

    @Test
    @DisplayName("계정 삭제 — 개인정보를 익명화하고 소셜 연동·푸시 토큰·리프레시 토큰·알림·방문 기록·사진을 바로 지우며, 재가입 차단용 이메일 해시를 남긴다(나머지는 30일 뒤 배치)")
    void deleteAccountAnonymizesAndRevokesCredentials() {
        User user = user(42L, "delete-me@dev.local", "password1234");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        service(List.of()).deleteAccount(42L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.TERMINATED);
        assertThat(user.getHandle()).isEqualTo("deleted_42");
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getPreferredCategories()).isEmpty();
        verify(identityRepository).deleteAllByUserId(42L);
        verify(deviceRepository).deleteAllByUserId(42L);
        verify(refreshTokenRepository).deleteAllByUserId(42L);
        verify(deletedEmailHashRepository).save(any());
        verify(emailVerificationRepository).deleteAllByEmail("delete-me@dev.local");
        verify(accountEraser).erase(user);
        // 문의·게시물 등 나머지 기록은 30일 유예기간 뒤 AccountDeletionJob 이 지운다.
        verify(inquiryRepository, never()).deleteAllByUserId(any());
    }

    // ───────────── 소셜 연동 상태 · 해제 ─────────────

    @Test
    @DisplayName("내 정보 — 연동된 소셜 provider 를 중복 없이 enum 순서로, 비밀번호 유무와 함께 내려준다")
    void meIncludesLinkedProvidersAndPasswordFlag() {
        User user = user(5L, "me@dev.local", "password1234");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(identityRepository.findAllByUserId(5L)).thenReturn(List.of(
                new UserIdentity(5L, AuthProvider.KAKAO, "kakao-5"),
                new UserIdentity(5L, AuthProvider.APPLE, "apple-5"),
                new UserIdentity(5L, AuthProvider.KAKAO, "kakao-5b")));

        MeResponse me = service(List.of()).me(5L);

        assertThat(me.linkedProviders()).containsExactly(AuthProvider.APPLE, AuthProvider.KAKAO);
        assertThat(me.hasPassword()).isTrue();
    }

    @Test
    @DisplayName("연동 해제 — 그 provider 의 연동만 지우고 남은 연동을 돌려준다")
    void unlinkSocialRemovesOnlyThatProvider() {
        User user = user(6L, "social@dev.local", null);
        UserIdentity kakao = new UserIdentity(6L, AuthProvider.KAKAO, "kakao-6");
        UserIdentity google = new UserIdentity(6L, AuthProvider.GOOGLE, "google-6");
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(identityRepository.findAllByUserId(6L)).thenReturn(List.of(kakao, google));

        MeResponse me = service(List.of()).unlinkSocial(6L, AuthProvider.KAKAO);

        verify(identityRepository).deleteAll(List.of(kakao));
        assertThat(me.linkedProviders()).containsExactly(AuthProvider.GOOGLE);
        assertThat(me.hasPassword()).isFalse();
    }

    @Test
    @DisplayName("연동 해제 — 연동되지 않은 provider 면 SOCIAL_ACCOUNT_NOT_LINKED, 아무것도 지우지 않는다")
    void unlinkSocialNotLinked() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "u7@dev.local", "password1234")));
        when(identityRepository.findAllByUserId(7L))
                .thenReturn(List.of(new UserIdentity(7L, AuthProvider.GOOGLE, "google-7")));

        assertApiError(() -> service(List.of()).unlinkSocial(7L, AuthProvider.APPLE), ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        verify(identityRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("연동 해제 — 비밀번호 없는 계정의 마지막 연동은 LAST_LOGIN_METHOD, 비밀번호가 있으면 해제된다")
    void unlinkSocialLastLoginMethod() {
        UserIdentity onlyKakao = new UserIdentity(8L, AuthProvider.KAKAO, "kakao-8");
        when(identityRepository.findAllByUserId(8L)).thenReturn(List.of(onlyKakao));

        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L, null, null)));
        assertApiError(() -> service(List.of()).unlinkSocial(8L, AuthProvider.KAKAO), ErrorCode.LAST_LOGIN_METHOD);
        verify(identityRepository, never()).deleteAll(any());

        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L, "u8@dev.local", "password1234")));
        MeResponse me = service(List.of()).unlinkSocial(8L, AuthProvider.KAKAO);
        verify(identityRepository).deleteAll(List.of(onlyKakao));
        assertThat(me.linkedProviders()).isEmpty();
    }
}
