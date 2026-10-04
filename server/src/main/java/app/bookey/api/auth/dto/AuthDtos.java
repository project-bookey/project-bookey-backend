package app.bookey.api.auth.dto;

import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.DevicePlatform;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    /** 소셜 로그인·연동. token 은 provider 가 발급한 idToken/accessToken. */
    public record SocialLoginRequest(
            @NotNull AuthProvider provider,
            @NotBlank String token
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** 인증 코드 발급 요청 — 가입 코드와 비밀번호 재설정 코드가 같은 모양을 쓴다. */
    public record EmailCodeRequest(
            @NotBlank @Email @Size(max = 255) String email
    ) {}

    /** 가입 전에 이메일 코드만 먼저 확인한다. 성공해도 코드는 소진하지 않고 실제 가입 때 다시 검증한다. */
    public record EmailCodeVerifyRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 6) String code
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
            @Size(max = 100) String identityVerificationId,
            @NotNull Boolean termsAgreed,
            @NotBlank @Size(max = 20) String termsVersion,
            @NotNull Boolean privacyAgreed,
            @NotBlank @Size(max = 20) String privacyVersion
    ) {}

    /** 가입 화면 구성용 — 어떤 인증을 요구하는지, 포트원 SDK 키, 개발 스텁 여부. */
    public record SignupConfigResponse(
            @NotNull String verification,
            String portoneStoreId,
            String portoneChannelKey,
            boolean identityDevStub
    ) {}

    /** 비밀번호 재설정 — 이메일로 받은 6자리 코드와 새 비밀번호. 성공하면 바로 로그인된다. */
    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank @Size(min = 8, max = 72) String newPassword
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
            java.util.List<String> preferredCategories,
            /** 이 계정에 연동된 소셜 로그인(enum 순서) — 앱 설정의 연동 카드가 상태를 그린다. */
            java.util.List<AuthProvider> linkedProviders,
            /** 비밀번호가 있는지 — 없으면(소셜 전용 계정) 마지막 소셜 연동은 해제할 수 없다. */
            boolean hasPassword
    ) {}

    public record DeviceRegisterRequest(
            @NotNull DevicePlatform platform,
            @NotBlank String pushToken,
            Boolean pushEnabled
    ) {}
}
