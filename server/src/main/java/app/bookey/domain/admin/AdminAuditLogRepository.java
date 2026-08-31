package app.bookey.domain.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 조회.
 * 널 조건은 파라미터가 아니라 메서드 선택으로 표현한다 — PostgreSQL 의 null 파라미터 타입 추론 문제를 피한다.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminAuditLog> findAllByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    Page<AdminAuditLog> findAllByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AdminAuditLog> findAllByAdminIdAndActionOrderByCreatedAtDesc(
            Long adminId, String action, Pageable pageable);
}
