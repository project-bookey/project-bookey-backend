package app.bookey.domain.novel;

import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NovelRefreshPurchaseRepository extends JpaRepository<NovelRefreshPurchase, Long> {
    /** 확인할 주문을 잠가 가져온다 — 앱이 확인을 두 번 보내도 새로고침을 한 번만 더한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from NovelRefreshPurchase p where p.userId = :userId and p.orderId = :orderId")
    Optional<NovelRefreshPurchase> lockByUserIdAndOrderId(@Param("userId") Long userId, @Param("orderId") String orderId);

    /** 이 스토어 결제로 이미 확인한 주문이 있는지. */
    boolean existsByProviderAndTransactionId(SubscriptionStore provider, String transactionId);
}
