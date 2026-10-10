package app.bookey.domain.admin;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 감사 로그 조회.
 * 널 조건은 바인딩 파라미터가 아니라 조건을 빼는 것으로 표현한다 — PostgreSQL 의 null 파라미터 타입 추론(bytea) 문제를 피한다.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long>,
        JpaSpecificationExecutor<AdminAuditLog> {

    /** 주어진 조건만 AND 로 묶는다. 모두 null 이면 전체. */
    static Specification<AdminAuditLog> matching(Long adminId, String action, String targetType, Long targetId,
                                                 Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (adminId != null) predicates.add(cb.equal(root.get("adminId"), adminId));
            if (action != null) predicates.add(cb.equal(root.get("action"), action));
            if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
            if (targetId != null) predicates.add(cb.equal(root.get("targetId"), targetId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
