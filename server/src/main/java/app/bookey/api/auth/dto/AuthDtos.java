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
            /** EMAIL_CODE 모드 — 이메일로 받은 6자리 인증 코드. */
            @Size(min = 6, max = 6) String code,
            /** IDENTITY 모드 — 포트원 본인인증 완료 id. */
            @Size(max = 100) String identityVerificationId
    ) {}

    /** 가입 화면 구성용 — 어떤 인증을 요구하는지, 포트원 SDK 키, 개발 스텁 여부. */
    public record SignupConfigResponse(
            @NotNull String verification,
            String portoneStoreId,
            String portoneChannelKey,
            boolean identityDevStub
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
            String gender,
            java.time.LocalDate birthDate,
            @NotNull String timezone,
            @NotNull String notifyTone,
            short quietHoursStart,
            short quietHoursEnd,
            short dailyNotifyCap,
            short clubNotifyCap,
            boolean allowNudge,
            @NotNull String status,
            /** 온보딩에서 고른 선호 카테고리. */
            java.util.List<String> preferredCategories
    ) {}

    public record DeviceRegisterRequest(
            @NotNull DevicePlatform platform,
            @NotBlank String pushToken,
            Boolean pushEnabled
    ) {}
}
