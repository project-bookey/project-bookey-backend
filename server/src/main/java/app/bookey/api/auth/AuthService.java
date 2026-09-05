package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
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

    private final SecureRandom secureRandom = new SecureRandom();

    private Map<AuthProvider, SocialTokenVerifier> verifierMap;

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

    /** 소셜 로그인 — 이미 연동된 계정만 통과한다. 신규 가입은 이메일 가입(코드 인증) 후 연동으로만 가능하다. */
    @Transactional
    public TokenResponse socialLogin(SocialLoginRequest request) {
        SocialProfile profile = verifierFor(request.provider()).verify(request.token());

        UserIdentity identity = identityRepository
                .findByProviderAndProviderUid(profile.provider(), profile.providerUid())
                .orElseThrow(() -> ApiException.of(ErrorCode.SOCIAL_SIGNUP_DISABLED));

        User user = userRepository.findById(identity.getUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueTokens(user, false);
    }

    /** 가입 인증 코드 발급 — 코드 원문은 저장하지 않고 해시만 남긴 뒤 이메일로 발송한다. */
    @Transactional
    public EmailCodeResponse requestEmailCode(EmailCodeRequest request) {
        requireSignupOpen();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        BookeyProperties.Auth.EmailCode policy = properties.auth().emailCode();
        Instant now = Instant.now();
        emailVerificationRepository.findTopByEmailOrderByIdDesc(email).ifPresent(latest -> {
            if (latest.getCreatedAt() != null && now.isBefore(latest.getCreatedAt().plus(policy.cooldown()))) {
                throw new ApiException(ErrorCode.RATE_LIMITED, "인증 코드는 잠시 후 다시 요청할 수 있습니다.");
            }
        });
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        emailVerificationRepository.save(new EmailVerification(email, sha256(code), now.plus(policy.ttl())));
        emailCodeSender.send(email, code, policy.ttl());
        return new EmailCodeResponse(policy.ttl().toSeconds(), policy.expose() ? code : null);
    }

    /**
     * 이메일 가입 — 발급받은 인증 코드를 통과해야 한다.
     * noRollbackFor: 코드 불일치 시 실패 횟수 누적이 롤백으로 사라지지 않게 한다(무작위 대입 방어).
     * ApiException 은 항상 다른 쓰기 이전에 던져지므로 부분 커밋 위험이 없다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse emailSignup(EmailSignupRequest request) {
        requireSignupOpen();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        consumeEmailCode(email, request.code());
        User user = User.builder()
                .handle(handleGenerator.generate(email.substring(0, email.indexOf("@"))))
                .email(email)
                .nickname(request.nickname().trim())
                .build();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.markEmailVerified(Instant.now());
        return issueTokens(userRepository.save(user), true);
    }

    /** 최신 발급 코드와 대조한다 — 소진·만료·시도 초과면 재발급을 유도하고, 불일치는 실패 횟수를 누적한다. */
    private void consumeEmailCode(String email, String code) {
        BookeyProperties.Auth.EmailCode policy = properties.auth().emailCode();
        EmailVerification verification = emailVerificationRepository.findTopByEmailOrderByIdDesc(email)
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
        verification.consume(now);
        emailVerificationRepository.save(verification);
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

    public static MeResponse toMe(User user) {
        return new MeResponse(
                user.getId(), user.getHandle(), user.getNickname(), user.getEmail(),
                user.getAvatarUrl(), user.getTimezone(), user.getNotifyTone().name(),
                user.getQuietHoursStart(), user.getQuietHoursEnd(),
                user.getDailyNotifyCap(), user.getClubNotifyCap(),
                user.isAllowNudge(), user.getStatus().name());
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
