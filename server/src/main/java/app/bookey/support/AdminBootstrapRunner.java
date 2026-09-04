package app.bookey.support;

import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 최초 관리자 생성기.
 *
 * 관리자 계정이 하나도 없고 ADMIN_BOOTSTRAP_EMAIL/PASSWORD 가 모두 있을 때만 실행한다.
 * 최초 로그인 후에는 Cloud Run 환경변수와 GitHub Secrets 에서 bootstrap 값을 제거한다.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }

        String email = System.getenv("ADMIN_BOOTSTRAP_EMAIL");
        String password = System.getenv("ADMIN_BOOTSTRAP_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admin account exists, but ADMIN_BOOTSTRAP_EMAIL/PASSWORD are not configured");
            return;
        }

        Admin admin = new Admin(
                email.trim().toLowerCase(java.util.Locale.ROOT),
                passwordEncoder.encode(password),
                "운영 관리자",
                AdminRole.SUPER_ADMIN);
        adminRepository.save(admin);
        log.info("Bootstrapped initial admin account: {}", admin.getEmail());
    }
}
