package app.bookey.api.auth.dto;

import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    /** 소셜 로그인. token 은 provider 가 발급한 idToken/accessToken. */
    public record SocialLoginRequest(
            @NotNull AuthProvider provider,
            @NotBlank String token,
            /** 최초 가입 시에만 사용. 없으면 provider 프로필에서 가져온다. */
            @Size(max = 50) String nickname
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(
            @NotNull String accessToken,
            @NotNull String refreshToken,
            long expiresInSec,
            boolean newUser,
            @NotNull MeResponse user
    ) {}

    public record MeResponse(
            @NotNull Long id,
            @NotNull String handle,
            @NotNull String nickname,
            String email,
            String avatarUrl,
            @NotNull String timezone,
            @NotNull String notifyTone,
            short quietHoursStart,
            short quietHoursEnd,
            short dailyNotifyCap,
            short clubNotifyCap,
            boolean allowNudge,
            @NotNull String status
    ) {}

    public record DeviceRegisterRequest(
            @NotNull DevicePlatform platform,
            @NotBlank String pushToken,
            Boolean pushEnabled
    ) {}
}
