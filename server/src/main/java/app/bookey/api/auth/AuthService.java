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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final JwtTokenProvider tokenProvider;
    private final HandleGenerator handleGenerator;
    private final BookeyProperties properties;
    private final List<SocialTokenVerifier> verifiers;

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

    @Transactional
    public TokenResponse socialLogin(SocialLoginRequest request) {
        SocialProfile profile = verifierFor(request.provider()).verify(request.token());

        UserIdentity identity = identityRepository
                .findByProviderAndProviderUid(profile.provider(), profile.providerUid())
                .orElse(null);

        boolean newUser = identity == null;
        User user;
        if (newUser) {
            requireSignupOpen();
            user = createUser(profile, request.nickname());
            identityRepository.save(
                    new UserIdentity(user.getId(), profile.provider(), profile.providerUid()));
        } else {
            user = userRepository.findById(identity.getUserId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        }

        if (!user.getStatus().canLogin()) {
            throw ApiException.of(ErrorCode.USER_SUSPENDED);
        }
        return issueTokens(user, newUser);
    }

    private void requireSignupOpen() {
        opsFlagRepository.findById(OpsFlag.SIGNUP_OPEN).ifPresent(flag -> {
            if (!flag.isEnabled()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "현재 신규 가입이 중단되었습니다.");
            }
        });
    }

    private User createUser(SocialProfile profile, String requestedNickname) {
        String nickname = firstNonBlank(requestedNickname, profile.nickname(), "독서가");
        String handleSeed = profile.email() != null
                ? profile.email().split("@")[0]
                : nickname;
        User user = User.builder()
                .handle(handleGenerator.generate(handleSeed))
                .email(profile.email())
                .nickname(nickname)
                .avatarUrl(profile.avatarUrl())
                .timezone("Asia/Seoul")
                .build();
        return userRepository.save(user);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "독서가";
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
