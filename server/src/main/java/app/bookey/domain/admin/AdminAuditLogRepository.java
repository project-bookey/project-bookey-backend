package app.bookey.domain.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query("""
            SELECT l FROM AdminAuditLog l
            WHERE (:adminId IS NULL OR l.adminId = :adminId)
              AND (:action  IS NULL OR l.action = :action)
            ORDER BY l.createdAt DESC
            """)
    Page<AdminAuditLog> search(@Param("adminId") Long adminId,
                               @Param("action") String action,
                               Pageable pageable);
}
