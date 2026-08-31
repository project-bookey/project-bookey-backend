package app.bookey.domain.report;

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
}
