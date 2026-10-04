package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.*;
import app.bookey.api.legal.ConsentService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.user.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final UserDeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpsFlagRepository opsFlagRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtTokenProvider tokenProvider;
    private final HandleGenerator handleGenerator;
    private final BookeyProperties properties;
    private final List<SocialTokenVerifier> verifiers;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeSender emailCodeSender;
    private final IdentityVerifier identityVerifier;
    private final InquiryRepository inquiryRepository;
    private final DeletedEmailHashRepository deletedEmailHashRepository;
    private final ConsentService consentService;
    private final AccountEraser accountEraser;

    private final SecureRandom secureRandom = new SecureRandom();

    private Map<AuthProvider, SocialTokenVerifier> verifierMap;

    /** 계정을 종료하고 재로그인에 필요한 모든 로컬 자격 증명과 개인정보를 제거한다. */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Instant now = Instant.now();
        if (user.getEmail() != null) {
            String normalizedEmail = normalizeEmail(user.getEmail());
            deletedEmailHashRepository.save(new DeletedEmailHash(sha256(normalizedEmail), now));
            emailVerificationRepository.deleteAllByEmail(normalizedEmail);
        }
        identityRepository.deleteAllByUserId(userId);
        deviceRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        // 알림·방문 기록·올린 프로필 사진 파일은 바로 지운다(AccountEraser).
        // 나머지 기록은 30일 유예기간 뒤 AccountDeletionJob이 FK cascade로 삭제한다.
        accountEraser.erase(user);
        user.anonymizeForDeletion(now);
    }

    private SocialTokenVerifier verifierFor(AuthProvider provider) {
        if (verifierMap == null) {
            Map<AuthProvider, SocialTokenVerifier> map = new EnumMap<>(AuthProvider.class);
            verifiers.forEach(v -> map.put(v.provider(), v));
            verifierMap = map;
        }
        SocialTokenVerifier verifier = verifierMap.get(provider);
        if (verifier == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지원하지 않는 로그인 방식입니다.");
        }
        return verifier;
    }

    /**
     * 소셜 로그인 — 연동된 계정은 로그인하고, 처음 보는 소셜 계정은 가입 동의가 함께 오면 가입시킨다.
     * 동의가 없으면 계정을 만들지 않고 LEGAL_CONSENT_REQUIRED — 앱이 동의를 받아 같은 token 으로 다시 부른다.
     */
    @Transactional
    public TokenResponse socialLogin(SocialLoginRequest request) {
        SocialProfile profile = verifierFor(request.provider()).verify(request.token());

        var existingIdentity = identityRepository
                .findByProviderAndProviderUid(profile.provider(), profile.providerUid());
        if (existingIdentity.isEmpty()) {
            return socialSignup(profile, request.consent());
        }

        User user = userRepository.findById(existingIdentity.get().getUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueTokens(user, false);
    }

    private TokenResponse socialSignup(SocialProfile profile, SignupConsent consent) {
        requireSignupOpen();
        String email = normalizeNullableEmail(profile.email());
        if (email != null) requireEmailAvailable(email);
        // 이메일 충돌을 먼저 본다 — 동의를 다 받고 나서야 가입할 수 없는 계정이라고 알리지 않게.
        consentService.requireSignupConsent(consent);
        User user = User.builder()
                .handle(handleGenerator.generate(handleSeed(profile)))
                .email(email)
                .nickname(availableNickname(displayName(profile)))
                .avatarUrl(profile.avatarUrl())
                .build();
        if (email != null) {
            user.markEmailVerified(Instant.now());
        }
        User saved = userRepository.save(user);
        identityRepository.save(new UserIdentity(saved.getId(), profile.provider(), profile.providerUid()));
        consentService.recordSignup(saved.getId(), consent, Instant.now());
        return issueTokens(saved, true);
    }

    /** 가입 인증 코드 발급 — 코드 원문은 저장하지 않고 해시만 남긴 뒤 이메일로 발송한다. */
    @Transactional
    public EmailCodeResponse requestEmailCode(EmailCodeRequest request) {
        requireSignupOpen();
        String email = normalizeEmail(request.email());
        requireEmailAvailable(email);
        return issueEmailCode(email, EmailCodePurpose.SIGNUP);
    }

    /** 가입 폼에서 코드를 미리 확인한다. 성공한 코드는 가입 트랜잭션에서 다시 확인하고 그때 소진한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifySignupEmailCode(EmailCodeVerifyRequest request) {
        requireSignupOpen();
        String email = normalizeEmail(request.email());
        requireEmailAvailable(email);
        validateEmailCode(email, request.code(), EmailCodePurpose.SIGNUP, false);
    }

    /**
     * 비밀번호 재설정 코드 발급 — 가입된 이메일에만 보낸다.
     * 가입 여부는 가입 코드 발급(EMAIL_ALREADY_EXISTS)에서도 이미 드러나므로 여기서 숨겨도 얻는 게 없다 —
     * 오타 낸 사람이 오지 않을 메일을 기다리지 않게 바로 알린다.
     * 소셜로만 가입한 계정도 이메일이 있으면 받을 수 있다 — 재설정하면 그 계정에 비밀번호가 생긴다.
     */
    @Transactional
    public EmailCodeResponse requestPasswordResetCode(EmailCodeRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_NOT_REGISTERED));
        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueEmailCode(email, EmailCodePurpose.PASSWORD_RESET);
    }

    /** 코드 발급 공통 — 같은 용도의 마지막 코드 기준 쿨다운을 지키고, 해시만 저장한 뒤 발송한다. */
    private EmailCodeResponse issueEmailCode(String email, EmailCodePurpose purpose) {
        BookeyProperties.Auth.EmailCode policy = properties.auth().emailCode();
        Instant now = Instant.now();
        emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose).ifPresent(latest -> {
            if (latest.getCreatedAt() != null && now.isBefore(latest.getCreatedAt().plus(policy.cooldown()))) {
                throw new ApiException(ErrorCode.RATE_LIMITED, "인증 코드는 잠시 후 다시 요청할 수 있습니다.");
            }
        });
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        emailVerificationRepository.save(new EmailVerification(email, purpose, sha256(code), now.plus(policy.ttl())));
        emailCodeSender.send(email, code, policy.ttl(), purpose);
        return new EmailCodeResponse(policy.ttl().toSeconds(), policy.expose() ? code : null);
    }

    /**
     * 비밀번호 재설정 — 코드가 맞으면 새 비밀번호로 바꾸고, 다른 기기의 리프레시 토큰을 모두 폐기한 뒤 로그인시킨다.
     * 코드를 받았다는 건 이메일을 가졌다는 뜻이라 이메일 인증 시각도 채운다.
     * noRollbackFor: 코드 불일치 실패 횟수가 롤백으로 사라지지 않게 한다 — 그 밖의 ApiException 은 쓰기 이전에 던져진다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse resetPassword(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_CODE_INVALID));
        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        validateEmailCode(email, request.code(), EmailCodePurpose.PASSWORD_RESET, true);
        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.markEmailVerified(now);
        refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        return issueTokens(user, false);
    }

    /** 가입 화면 구성 — 어떤 인증을 요구하는지와 포트원 SDK 키. */
    public SignupConfigResponse signupConfig() {
        BookeyProperties.Auth auth = properties.auth();
        BookeyProperties.Auth.Identity identity = auth.identity();
        boolean configured = identity.portoneApiSecret() != null && !identity.portoneApiSecret().isBlank();
        return new SignupConfigResponse(
                auth.signupVerification().name(),
                blankToNull(identity.portoneStoreId()),
                blankToNull(identity.portoneChannelKey()),
                !configured && identity.allowDevStub());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 이메일 가입 — 설정에 따라 이메일 인증 코드(EMAIL_CODE), 휴대폰 본인인증(IDENTITY),
     * 또는 인증 생략(NONE)을 적용한다.
     * noRollbackFor: 코드 불일치 시 실패 횟수 누적이 롤백으로 사라지지 않게 한다(무작위 대입 방어).
     * ApiException 은 항상 다른 쓰기 이전에 던져지므로 부분 커밋 위험이 없다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse emailSignup(EmailSignupRequest request) {
        requireSignupOpen();
        consentService.requireSignupConsent(request.consent());
        String email = normalizeEmail(request.email());
        requireEmailAvailable(email);
        String nickname = request.nickname().trim();
        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw ApiException.of(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        VerifiedIdentity identity = null;
        switch (properties.auth().signupVerification()) {
            case EMAIL_CODE -> {
                if (request.code() == null || request.code().isBlank()) {
                    throw ApiException.of(ErrorCode.EMAIL_CODE_INVALID);
                }
                validateEmailCode(email, request.code(), EmailCodePurpose.SIGNUP, true);
            }
            case IDENTITY -> identity = requireVerifiedIdentity(request.identityVerificationId());
            case NONE -> {
                // 개발·초기 테스트용. 운영에서 쓰면 이메일 소유 검증 없이 가입된다.
            }
        }
        User user = User.builder()
                .handle(handleGenerator.generate(email.substring(0, email.indexOf("@"))))
                .email(email)
                .nickname(nickname)
                .build();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        Instant now = Instant.now();
        if (identity != null) {
            user.recordIdentity(identity.name(), identity.phone(), identity.birthDate(),
                    identity.ci(), identity.di(), now);
        } else {
            user.markEmailVerified(now);
        }
        User saved = userRepository.save(user);
        consentService.recordSignup(saved.getId(), request.consent(), now);
        return issueTokens(saved, true);
    }

    /** 본인인증 결과를 포트원에서 재조회하고, 같은 사람(CI)의 중복 가입을 막는다. */
    private VerifiedIdentity requireVerifiedIdentity(String identityVerificationId) {
        if (identityVerificationId == null || identityVerificationId.isBlank()) {
            throw ApiException.of(ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
        }
        VerifiedIdentity identity = identityVerifier.verify(identityVerificationId.trim());
        if (userRepository.existsByCi(identity.ci())) {
            throw ApiException.of(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }
        return identity;
    }

    /** 최신 발급 코드와 대조한다 — 소진·만료·시도 초과면 재발급을 유도하고, 불일치는 실패 횟수를 누적한다. */
    private void validateEmailCode(String email, String code, EmailCodePurpose purpose, boolean consume) {
        BookeyProperties.Auth.EmailCode policy = properties.auth().emailCode();
        EmailVerification verification = emailVerificationRepository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_CODE_INVALID));
        Instant now = Instant.now();
        if (verification.isConsumed() || verification.isExpired(now)
                || !verification.hasAttemptsLeft(policy.maxAttempts())) {
            throw ApiException.of(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!sha256(code).equals(verification.getCodeHash())) {
            verification.recordFailedAttempt();
            emailVerificationRepository.save(verification);
            throw ApiException.of(ErrorCode.EMAIL_CODE_INVALID);
        }
        if (consume) {
            verification.consume(now);
            emailVerificationRepository.save(verification);
        }
    }

    @Transactional
    public TokenResponse emailLogin(EmailLoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueTokens(user, false);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeNullableEmail(String email) {
        return email == null || email.isBlank() ? null : normalizeEmail(email);
    }

    private void requireEmailAvailable(String email) {
        if (deletedEmailHashRepository.existsById(sha256(normalizeEmail(email)))) {
            throw ApiException.of(ErrorCode.EMAIL_REJOIN_BLOCKED);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private String availableNickname(String requested) {
        String trimmed = requested.trim();
        String base = trimmed.substring(0, Math.min(trimmed.length(), 50));
        if (!userRepository.existsByNicknameIgnoreCase(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String tail = "_" + suffix;
            String candidate = base.substring(0, Math.min(base.length(), 50 - tail.length())) + tail;
            if (!userRepository.existsByNicknameIgnoreCase(candidate)) return candidate;
        }
        throw ApiException.of(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    private static String handleSeed(SocialProfile profile) {
        String email = normalizeNullableEmail(profile.email());
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        if (profile.nickname() != null && !profile.nickname().isBlank()) {
            return profile.nickname();
        }
        return profile.provider().name().toLowerCase(java.util.Locale.ROOT) + profile.providerUid();
    }

    private static String displayName(SocialProfile profile) {
        if (profile.nickname() != null && !profile.nickname().isBlank()) {
            return profile.nickname().trim();
        }
        String email = profile.email();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "bookey";
    }

    @Transactional
    public MeResponse linkSocial(Long userId, SocialLoginRequest request) {
        SocialProfile profile = verifierFor(request.provider()).verify(request.token());
        identityRepository.findByProviderAndProviderUid(profile.provider(), profile.providerUid()).ifPresent(identity -> {
            if (!identity.getUserId().equals(userId)) {
                throw ApiException.of(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
            }
        });
        if (identityRepository.findByProviderAndProviderUid(profile.provider(), profile.providerUid()).isEmpty()) {
            userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            identityRepository.save(new UserIdentity(userId, profile.provider(), profile.providerUid()));
        }
        return toMe(userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
    }

    /**
     * 소셜 연동 해제 — 그 provider 의 연동을 지운다. 비밀번호가 없는(소셜로 가입한) 계정의 마지막 연동은
     * 지우면 다시 로그인할 길이 없어 막는다.
     */
    @Transactional
    public MeResponse unlinkSocial(Long userId, AuthProvider provider) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<UserIdentity> identities = identityRepository.findAllByUserId(userId);
        List<UserIdentity> target = identities.stream().filter(i -> i.getProvider() == provider).toList();
        if (target.isEmpty()) {
            throw ApiException.of(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        }
        if (user.getPasswordHash() == null && target.size() == identities.size()) {
            throw ApiException.of(ErrorCode.LAST_LOGIN_METHOD);
        }
        identityRepository.deleteAll(target);
        List<UserIdentity> remaining = identities.stream().filter(i -> i.getProvider() != provider).toList();
        return toMe(user, providersOf(remaining));
    }

    private void requireSignupOpen() {
        opsFlagRepository.findById(OpsFlag.SIGNUP_OPEN).ifPresent(flag -> {
            if (!flag.isEnabled()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "현재 신규 가입이 중단되었습니다.");
            }
        });
    }

    private TokenResponse issueTokens(User user, boolean newUser) {
        String accessToken = tokenProvider.createUserAccessToken(user.getId(), user.getHandle());
        String refreshToken = tokenProvider.createUserRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), sha256(refreshToken),
                Instant.now().plus(tokenProvider.refreshTtl())));
        return new TokenResponse(accessToken, refreshToken,
                tokenProvider.accessTtl().toSeconds(), newUser, toMe(user));
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = tokenProvider.parse(request.refreshToken(), TokenType.USER_REFRESH);
        Long userId = tokenProvider.subjectId(claims);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_TOKEN));
        if (!stored.isUsable() || !stored.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
        // 회전: 사용한 리프레시 토큰은 즉시 폐기한다.
        stored.revoke();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueTokens(user, false);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        deviceRepository.findAllByUserIdAndPushEnabledTrue(userId)
                .forEach(UserDevice::disablePush);
    }

    @Transactional
    public void registerDevice(Long userId, DeviceRegisterRequest request) {
        boolean enabled = request.pushEnabled() == null || request.pushEnabled();
        deviceRepository.findByPlatformAndPushToken(request.platform(), request.pushToken())
                .ifPresentOrElse(
                        device -> device.touch(userId, enabled),
                        () -> deviceRepository.save(
                                new UserDevice(userId, request.platform(), request.pushToken())));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return toMe(userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
    }

    /** 내 정보 응답 — 연동된 소셜과 동의 상태는 저장소에서 읽어 함께 내려준다. */
    public MeResponse toMe(User user) {
        return toMe(user, providersOf(identityRepository.findAllByUserId(user.getId())));
    }

    private MeResponse toMe(User user, List<AuthProvider> linkedProviders) {
        return new MeResponse(
                user.getId(), user.getHandle(), user.getNickname(), user.getEmail(),
                user.getAvatarUrl(), user.getGender(), user.getBirthDate(),
                user.getTimezone(), user.getNotifyTone().name(),
                user.getQuietHoursStart(), user.getQuietHoursEnd(),
                user.getDailyNotifyCap(), user.getClubNotifyCap(),
                user.isAllowNudge(), user.getStatus().name(),
                List.of(user.getPreferredCategories()),
                linkedProviders,
                user.getPasswordHash() != null,
                consentService.states(user.getId()));
    }

    /** 연동 목록 → provider 만, 중복 없이 enum 순서로. */
    private static List<AuthProvider> providersOf(List<UserIdentity> identities) {
        return identities.stream().map(UserIdentity::getProvider).distinct().sorted().toList();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
