package app.bookey.domain.wallet;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface BookmarkPurchaseRepository extends JpaRepository<BookmarkPurchase, Long>,
        JpaSpecificationExecutor<BookmarkPurchase> {

    Optional<BookmarkPurchase> findByUserIdAndOrderId(Long userId, String orderId);

    /**
     * 관리자 결제 검색. 주어진 조건만 AND 로 묶는다 — null 바인딩(bytea) 문제를 피하려고 조건을 빼는 쪽으로 표현한다.
     * orderId 는 앞부분 일치로 찾는다(주문번호를 일부만 받아 적는 경우가 많다).
     */
    static Specification<BookmarkPurchase> matching(Long userId, String orderId, BookmarkPurchaseStatus status,
                                                    SubscriptionStore provider) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (orderId != null) predicates.add(cb.like(root.get("orderId"), orderId.replace("%", "") + "%"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (provider != null) predicates.add(cb.equal(root.get("provider"), provider));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
