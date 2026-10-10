package app.bookey.admin.auth;

import app.bookey.admin.dto.AdminDtos.AdminProfile;
import app.bookey.admin.dto.AdminDtos.TotpSecretView;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.TotpVerifier;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AccessRevocations;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminCapability;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.admin.AdminStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private static final AuthAdmin SUPER = new AuthAdmin(1L, "root@bookey.app", AdminRole.SUPER_ADMIN);

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final TotpVerifier totpVerifier = mock(TotpVerifier.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessRevocations revocations = mock(AccessRevocations.class);
    private final AdminAuthService service = new AdminAuthService(adminRepository, passwordEncoder,
            mock(JwtTokenProvider.class), totpVerifier, auditService, mock(RateLimiter.class), revocations);

    private Admin admin(long id, AdminRole role) {
        Admin admin = new Admin("a" + id + "@bookey.app", "hash", "관리자" + id, role);
        try {
            Field f = Admin.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(admin, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(adminRepository.findById(id)).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    @DisplayName("시크릿 발급만으로는 2FA 가 켜지지 않는다 — 등록을 마치지 못해도 로그인이 막히지 않게")
    void prepareDoesNotEnable() {
        Admin admin = admin(1L, AdminRole.SUPER_ADMIN);
        when(totpVerifier.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");

        TotpSecretView view = service.prepareTotp(SUPER);

        assertThat(admin.isTotpEnabled()).isFalse();
        assertThat(admin.getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(view.otpauthUri()).startsWith("otpauth://totp/bookey-admin%3Aa1%40bookey.app?secret=JBSWY3DPEHPK3PXP");
    }

    @Test
    @DisplayName("이미 켜진 2FA 는 시크릿을 다시 발급할 수 없다 — 탈취한 토큰으로 가로채지 못하게")
    void cannotReissueWhenEnabled() {
        Admin admin = admin(1L, AdminRole.SUPER_ADMIN);
        admin.prepareTotp("OLD");
        admin.confirmTotp();

        assertThatThrownBy(() -> service.prepareTotp(SUPER))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_TOTP_ALREADY_ENABLED);
        assertThat(admin.getTotpSecret()).isEqualTo("OLD");
    }

    @Test
    @DisplayName("확인 코드가 틀리면 400 으로 거절하고 꺼진 채 둔다 — 맞으면 켜고 감사 로그를 남긴다")
    void confirm() {
        Admin admin = admin(1L, AdminRole.SUPER_ADMIN);
        admin.prepareTotp("SECRET");
        when(totpVerifier.verify("SECRET", "000000")).thenReturn(false);
        when(totpVerifier.verify("SECRET", "123456")).thenReturn(true);

        assertThatThrownBy(() -> service.confirmTotp(SUPER, "000000"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_TOTP_CODE_MISMATCH);
        assertThat(admin.isTotpEnabled()).isFalse();

        AdminProfile profile = service.confirmTotp(SUPER, "123456");
        assertThat(profile.totpEnabled()).isTrue();
        verify(auditService).log(eq(SUPER), eq("ENABLE_TOTP"), eq("ADMIN"), eq(1L), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("발급 없이 확인하면 NOT_PREPARED")
    void confirmWithoutPrepare() {
        admin(1L, AdminRole.SUPER_ADMIN);
        assertThatThrownBy(() -> service.confirmTotp(SUPER, "123456"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_TOTP_NOT_PREPARED);
    }

    @Test
    @DisplayName("자기 권한은 바꿀 수 없고, 마지막 최고 관리자는 강등할 수 없다")
    void changeRoleGuards() {
        admin(1L, AdminRole.SUPER_ADMIN);
        assertThatThrownBy(() -> service.changeRole(SUPER, 1L, AdminRole.VIEWER))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_SELF_ACTION);

        Admin other = admin(2L, AdminRole.SUPER_ADMIN);
        when(adminRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(1L);
        assertThatThrownBy(() -> service.changeRole(SUPER, 2L, AdminRole.OPERATOR))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LAST_SUPER);
        assertThat(other.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);

        when(adminRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(2L);
        service.changeRole(SUPER, 2L, AdminRole.OPERATOR);
        assertThat(other.getRole()).isEqualTo(AdminRole.OPERATOR);
    }

    @Test
    @DisplayName("내 정보에 역할에서 계산한 권한 목록이 실린다")
    void meCarriesCapabilities() {
        admin(5L, AdminRole.SUPPORT);
        assertThat(service.me(5L).capabilities())
                .containsExactly(AdminCapability.WARN, AdminCapability.HANDLE_SUPPORT, AdminCapability.VIEW_PAYMENTS);
    }

    @Test
    @DisplayName("관리자 정지 — 자기 자신과 마지막 최고 관리자는 정지할 수 없다")
    void changeStatusGuards() {
        admin(1L, AdminRole.SUPER_ADMIN);
        assertThatThrownBy(() -> service.changeStatus(SUPER, 1L, AdminStatus.SUSPENDED, "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_SELF_ACTION);

        Admin other = admin(2L, AdminRole.SUPER_ADMIN);
        when(adminRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE)).thenReturn(1L);
        assertThatThrownBy(() -> service.changeStatus(SUPER, 2L, AdminStatus.SUSPENDED, "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LAST_SUPER);

        Admin operator = admin(3L, AdminRole.OPERATOR);
        service.changeStatus(SUPER, 3L, AdminStatus.SUSPENDED, "퇴사");
        assertThat(operator.isActive()).isFalse();
        assertThat(other.isActive()).isTrue();
        verify(auditService).log(eq(SUPER), eq("SUSPEND_ADMIN"), eq("ADMIN"), eq(3L), eq("퇴사"), any(), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 — 그 관리자의 기존 로그인을 끊는다. 운영자는 못 한다")
    void resetPasswordRevokesTokens() {
        admin(3L, AdminRole.OPERATOR);
        when(passwordEncoder.encode("new-password-1234")).thenReturn("hashed");
        AuthAdmin operator = new AuthAdmin(4L, "op@bookey.app", AdminRole.OPERATOR);
        assertThatThrownBy(() -> service.resetPassword(operator, 3L, "new-password-1234", "분실"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);

        service.resetPassword(SUPER, 3L, "new-password-1234", "분실");

        verify(revocations).revokeAdmin(eq(3L), any());
    }

    @Test
    @DisplayName("내 비밀번호 변경 — 현재 비밀번호가 틀리면 400(로그아웃되지 않게), 맞으면 바꾸고 지금 로그인도 끊는다")
    void changeOwnPassword() {
        Admin me = admin(1L, AdminRole.SUPER_ADMIN);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.changeOwnPassword(SUPER, "wrong", "new-password-1234"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        when(passwordEncoder.matches("right", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-1234")).thenReturn("new-hash");
        service.changeOwnPassword(SUPER, "right", "new-password-1234");

        assertThat(me.getPasswordHash()).isEqualTo("new-hash");
        verify(revocations).revokeAdmin(eq(1L), any());
    }
}
