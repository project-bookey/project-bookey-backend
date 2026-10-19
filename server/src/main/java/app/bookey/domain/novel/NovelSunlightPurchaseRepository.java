package app.bookey.domain.novel;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NovelSunlightPurchaseRepository extends JpaRepository<NovelSunlightPurchase, Long> {
    /** 확인은 주문을 잠가 같은 주문이 동시에 두 번 채워지지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from NovelSunlightPurchase p where p.userId = :userId and p.orderId = :orderId")
    Optional<NovelSunlightPurchase> lockByUserIdAndOrderId(@Param("userId") Long userId, @Param("orderId") String orderId);
    boolean existsByTransactionId(String transactionId);
}
