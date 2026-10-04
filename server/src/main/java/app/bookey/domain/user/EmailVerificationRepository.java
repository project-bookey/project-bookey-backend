package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 이메일·용도별 최신 발급분 — 검증·재발급 쿨다운은 항상 그 용도의 마지막 코드 기준으로 판정한다. */
    Optional<EmailVerification> findTopByEmailAndPurposeOrderByIdDesc(String email, EmailCodePurpose purpose);

    void deleteAllByEmail(String email);

    /** 만료 시각이 cutoff 보다 이른 인증 기록을 지운다 — 보관 기간이 지난 이메일 주소를 남기지 않는다. */
    @Transactional
    @Modifying
    @Query("DELETE FROM EmailVerification v WHERE v.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
