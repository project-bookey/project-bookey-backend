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

import java.util.Map;

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

    @Operation(summary = "2FA 시크릿 발급")
    @PostMapping("/totp")
    public Map<String, String> issueTotp(@AuthenticationPrincipal AuthAdmin admin) {
        return adminAuthService.issueTotpSecret(admin.id());
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

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
