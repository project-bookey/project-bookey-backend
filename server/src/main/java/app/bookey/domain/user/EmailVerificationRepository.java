package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 이메일별 최신 발급분 — 검증·재발급 쿨다운은 항상 마지막 코드 기준으로 판정한다. */
    Optional<EmailVerification> findTopByEmailOrderByIdDesc(String email);
}
