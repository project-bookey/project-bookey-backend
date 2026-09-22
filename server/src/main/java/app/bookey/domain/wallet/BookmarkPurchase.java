package app.bookey.domain.wallet;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "bookmark_purchases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookmarkPurchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionStore provider;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "order_id", nullable = false, unique = true, length = 120)
    private String orderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "bonus_quantity", nullable = false)
    private int bonusQuantity;

    @Column(name = "amount_krw", nullable = false)
    private int amountKrw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookmarkPurchaseStatus status;

    @Builder
    private BookmarkPurchase(Long userId, SubscriptionStore provider, String productId, String orderId,
                             int quantity, int bonusQuantity, int amountKrw) {
        this.userId = userId;
        this.provider = provider;
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.bonusQuantity = bonusQuantity;
        this.amountKrw = amountKrw;
        this.status = BookmarkPurchaseStatus.PENDING;
    }

    public int totalQuantity() {
        return quantity + bonusQuantity;
    }

    public boolean isPaid() {
        return status == BookmarkPurchaseStatus.PAID;
    }

    public void markPaid(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = BookmarkPurchaseStatus.PAID;
    }
}
