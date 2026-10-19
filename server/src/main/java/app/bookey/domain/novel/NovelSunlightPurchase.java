package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import app.bookey.domain.wallet.BookmarkPurchaseStatus;
import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.persistence.*;
import lombok.*;

/** 햇살 충전 주문. 스토어 결제를 서버가 확인해야 PAID 가 되고 햇살이 들어간다. */
@Getter @Entity @Table(name = "novel_sunlight_purchases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelSunlightPurchase extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private SubscriptionStore provider;
    @Column(name = "product_id", nullable = false, length = 100) private String productId;
    @Column(name = "order_id", nullable = false, unique = true, length = 120) private String orderId;
    @Column(name = "transaction_id", unique = true, length = 200) private String transactionId;
    @Column(nullable = false) private int quantity;
    @Column(name = "amount_krw", nullable = false) private int amountKrw;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private BookmarkPurchaseStatus status;

    public NovelSunlightPurchase(Long userId, SubscriptionStore provider, String productId, String orderId,
                                 int quantity, int amountKrw) {
        this.userId = userId; this.provider = provider; this.productId = productId; this.orderId = orderId;
        this.quantity = quantity; this.amountKrw = amountKrw; this.status = BookmarkPurchaseStatus.PENDING;
    }
    public boolean isPaid() { return status == BookmarkPurchaseStatus.PAID; }
    public void markPaid(String transactionId) {
        this.transactionId = transactionId;
        this.status = BookmarkPurchaseStatus.PAID;
    }
}
