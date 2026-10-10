package app.bookey.domain.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** 최신 구독 한 건 — 활성 판정과 지급은 항상 마지막 구독 기준. */
    Optional<Subscription> findTopByUserIdOrderByIdDesc(Long userId);

    /** 관리자 회원 상세 — 구독 이력 전체(최근 순). */
    List<Subscription> findAllByUserIdOrderByIdDesc(Long userId);

    boolean existsByStoreAndOriginalTransactionId(SubscriptionStore store, String originalTransactionId);

    Optional<Subscription> findByStoreAndOriginalTransactionId(SubscriptionStore store,
                                                                 String originalTransactionId);
}
