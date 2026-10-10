package app.bookey.domain.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AbuseReportRepository extends JpaRepository<AbuseReport, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(String targetType, Long targetId, Long reporterId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    List<AbuseReport> findAllByTargetTypeAndTargetId(String targetType, Long targetId);

    @Modifying
    @Query("UPDATE AbuseReport r SET r.status = 'RESOLVED' WHERE r.targetType = :type AND r.targetId = :id")
    void resolveAllForTarget(@Param("type") String type, @Param("id") Long id);

    long countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    List<AbuseReport> findAllByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId);

    Page<AbuseReport> findAllByReporterIdOrderByIdDesc(Long reporterId, Pageable pageable);

    /** 대상별 신고 수 — 관리자 콘텐츠 목록에서 한 번에 센다. [targetId, count] */
    @Query("""
            SELECT r.targetId, COUNT(r) FROM AbuseReport r
            WHERE r.targetType = :type AND r.targetId IN :ids
            GROUP BY r.targetId
            """)
    List<Object[]> countByTargets(@Param("type") String type, @Param("ids") List<Long> ids);
}
