package app.bookey.domain.admin;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 서비스 사용자(users)와 완전히 분리된 관리자 계정 (§F13 보안 요구사항). */
@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AdminRole role = AdminRole.VIEWER;

    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    public Admin(String email, String passwordHash, String name, AdminRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.status = AdminStatus.ACTIVE;
    }

    public void recordLogin(String ip) {
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ip;
    }

    public void enableTotp(String secret) {
        this.totpSecret = secret;
        this.totpEnabled = true;
    }

    public void changeRole(AdminRole role) {
        this.role = role;
    }

    public void changeStatus(AdminStatus status) {
        this.status = status;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
    }
}
