package app.bookey.domain.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookmarkPurchaseRepository extends JpaRepository<BookmarkPurchase, Long> {
    Optional<BookmarkPurchase> findByUserIdAndOrderId(Long userId, String orderId);
}
