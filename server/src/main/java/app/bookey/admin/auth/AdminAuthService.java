package app.bookey.admin.auth;

import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.TotpVerifier;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    /** 무차별 대입 방어 — 계정당 분당 5회. */
    private static final int LOGIN_RATE_LIMIT = 5;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TotpVerifier totpVerifier;
    private final AdminAuditService auditService;
    private final RateLimiter rateLimiter;

    @Transactional
    public LoginResponse login(LoginRequest request, String ip) {
        rateLimiter.require("admin:login:" + request.email(), LOGIN_RATE_LIMIT, Duration.ofMinutes(1));

        Admin admin = adminRepository.findByEmail(request.email())
                .orElseThrow(() -> ApiException.of(ErrorCode.ADMIN_INVALID_CREDENTIALS));

        if (!admin.isActive()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "정지된 관리자 계정입니다.");
        }
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw ApiException.of(ErrorCode.ADMIN_INVALID_CREDENTIALS);
        }
        if (admin.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                return new LoginResponse(null, 0, true, null);
            }
            if (!totpVerifier.verify(admin.getTotpSecret(), request.totpCode())) {
                throw ApiException.of(ErrorCode.ADMIN_TOTP_INVALID);
            }
        }

        admin.recordLogin(ip);
        String token = tokenProvider.createAdminAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().name());

        auditService.log(new AuthAdmin(admin.getId(), admin.getEmail(), admin.getRole()),
                "LOGIN", "ADMIN", admin.getId(), null, null, Map.of("ip", ip));

        return new LoginResponse(token, 30 * 60L, false, toProfile(admin));
    }

    @Transactional(readOnly = true)
    public AdminProfile me(Long adminId) {
        return toProfile(adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
    }

    /** 2FA 설정 — 시크릿을 발급하고 코드 확인 후 활성화한다. */
    @Transactional
    public Map<String, String> issueTotpSecret(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        String secret = totpVerifier.generateSecret();
        admin.enableTotp(secret);
        String uri = "otpauth://totp/bookey-admin:" + admin.getEmail()
                + "?secret=" + secret + "&issuer=bookey";
        return Map.of("secret", secret, "otpauthUri", uri);
    }

    @Transactional
    public AdminProfile createAdmin(AuthAdmin actor, CreateAdminRequest request) {
        if (!actor.isSuper()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (adminRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 존재하는 관리자 이메일입니다.");
        }
        Admin admin = adminRepository.save(new Admin(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.role()));
        auditService.log(actor, "CREATE_ADMIN", "ADMIN", admin.getId(), null, null,
                Map.of("email", request.email(), "role", request.role().name()));
        return toProfile(admin);
    }

    @Transactional
    public void changeRole(AuthAdmin actor, Long adminId, AdminRole role) {
        if (!actor.isSuper()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        AdminRole before = admin.getRole();
        admin.changeRole(role);
        auditService.log(actor, "CHANGE_ADMIN_ROLE", "ADMIN", adminId, null,
                Map.of("role", before.name()), Map.of("role", role.name()));
    }

    private AdminProfile toProfile(Admin admin) {
        return new AdminProfile(admin.getId(), admin.getEmail(), admin.getName(),
                admin.getRole(), admin.isTotpEnabled(), admin.getLastLoginAt());
    }
}
