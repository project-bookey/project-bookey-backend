package app.bookey.admin.auth;

import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "Admin Auth", description = "관리자 인증 — 서비스 계정과 분리")
@RestController
@RequestMapping("/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "관리자 로그인 — 2FA 활성 계정은 totpCode 필수")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest servletRequest) {
        return adminAuthService.login(request, clientIp(servletRequest));
    }

    @Operation(summary = "내 관리자 정보")
    @GetMapping("/me")
    public AdminProfile me(@AuthenticationPrincipal AuthAdmin admin) {
        return adminAuthService.me(admin.id());
    }

    @Operation(summary = "2FA 등록 1단계 — 시크릿 발급(아직 켜지지 않음). 이미 켜져 있으면 409")
    @PostMapping("/totp")
    public TotpSecretView prepareTotp(@AuthenticationPrincipal AuthAdmin admin) {
        return adminAuthService.prepareTotp(admin);
    }

    @Operation(summary = "2FA 등록 2단계 — 인증 앱 코드를 확인하고 켠다")
    @PostMapping("/totp/confirm")
    public AdminProfile confirmTotp(@AuthenticationPrincipal AuthAdmin admin,
                                    @Valid @RequestBody TotpConfirmRequest request) {
        return adminAuthService.confirmTotp(admin, request.code());
    }

    @Operation(summary = "관리자 계정 생성 (SUPER_ADMIN)")
    @PostMapping("/admins")
    public AdminProfile create(@AuthenticationPrincipal AuthAdmin admin,
                               @Valid @RequestBody CreateAdminRequest request) {
        return adminAuthService.createAdmin(admin, request);
    }

    @Operation(summary = "관리자 권한 변경 (SUPER_ADMIN)")
    @PatchMapping("/admins/{adminId}/role")
    public ResponseEntity<Void> changeRole(@AuthenticationPrincipal AuthAdmin admin,
                                           @PathVariable Long adminId,
                                           @RequestParam AdminRole role) {
        adminAuthService.changeRole(admin, adminId, role);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 목록 (SUPER_ADMIN)")
    @GetMapping("/admins")
    public List<AdminRow> admins(@AuthenticationPrincipal AuthAdmin admin) {
        return adminAuthService.listAdmins(admin);
    }

    @Operation(summary = "관리자 정지 · 재활성화 (SUPER_ADMIN) — 자기 자신·마지막 최고 관리자는 불가")
    @PatchMapping("/admins/{adminId}/status")
    public ResponseEntity<Void> changeStatus(@AuthenticationPrincipal AuthAdmin admin,
                                             @PathVariable Long adminId,
                                             @Valid @RequestBody AdminStatusRequest request) {
        adminAuthService.changeStatus(admin, adminId, request.status(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 비밀번호 재설정 (SUPER_ADMIN) — 그 관리자의 기존 로그인은 끊긴다")
    @PutMapping("/admins/{adminId}/password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AuthAdmin admin,
                                              @PathVariable Long adminId,
                                              @Valid @RequestBody AdminPasswordResetRequest request) {
        adminAuthService.resetPassword(admin, adminId, request.newPassword(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 2FA 초기화 (SUPER_ADMIN) — 휴대폰 분실 대응")
    @DeleteMapping("/admins/{adminId}/totp")
    public ResponseEntity<Void> resetTotp(@AuthenticationPrincipal AuthAdmin admin,
                                          @PathVariable Long adminId,
                                          @RequestParam String reason) {
        adminAuthService.resetTotp(admin, adminId, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 비밀번호 변경 — 바꾸면 지금 로그인도 끊겨 다시 로그인한다")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthAdmin admin,
                                                  @Valid @RequestBody AdminPasswordChangeRequest request) {
        adminAuthService.changeOwnPassword(admin, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
