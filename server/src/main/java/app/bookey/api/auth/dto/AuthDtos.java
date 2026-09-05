package app.bookey.api.auth.dto;

import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.DevicePlatform;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    /** 소셜 로그인·연동. token 은 provider 가 발급한 idToken/accessToken. 신규 가입은 이메일 가입으로만 가능하다. */
    public record SocialLoginRequest(
            @NotNull AuthProvider provider,
            @NotBlank String token
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** 가입 인증 코드 발급 요청. */
    public record EmailCodeRequest(
            @NotBlank @Email @Size(max = 255) String email
    ) {}

    /** devCode 는 bookey.auth.email-code.expose=true(로컬)일 때만 담긴다. */
    public record EmailCodeResponse(
            long expiresInSec,
            String devCode
    ) {}

    public record EmailSignupRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 50) String nickname,
            /** 이메일로 받은 가입 인증 코드. */
            @NotBlank @Size(min = 6, max = 6) String code
    ) {}

    public record EmailLoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String password
    ) {}

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
