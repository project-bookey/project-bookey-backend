package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 홈 열매 밭 새로고침 결제 주문 — 릴레이노벨 앱의 소모성 상품, 한 번 사면 새로고침 한 번. 북키 책갈피 주문과 따로 둔다.
 * 스토어 결제를 확인하면 그 결제(transactionId)를 적고 paidAt 을 찍는다. 같은 결제로 다른 주문을 확인하지 못하게
 * (provider, transaction_id) 유니크를 건다.
 */
@Getter @Entity @Table(name = "novel_refresh_purchases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelRefreshPurchase extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private SubscriptionStore provider;
    @Column(name = "product_id", nullable = false, length = 100) private String productId;
    @Column(name = "order_id", nullable = false, unique = true, length = 120) private String orderId;
    /** 주문할 때의 설정 가격 — 기록용이고, 실제 금액은 스토어에 등록한 가격이다. */
    @Column(name = "amount_krw", nullable = false) private int amountKrw;
    /** 확인한 스토어 결제 — Apple transactionId, Google 구매 토큰. */
    @Column(name = "transaction_id", columnDefinition = "text") private String transactionId;
    @Column(name = "paid_at") private Instant paidAt;

    @Builder
    private NovelRefreshPurchase(Long userId, SubscriptionStore provider, String productId, String orderId, int amountKrw) {
        this.userId = userId; this.provider = provider; this.productId = productId;
        this.orderId = orderId; this.amountKrw = amountKrw;
    }

    public boolean isPaid() { return paidAt != null; }

    public void markPaid(String transactionId, Instant paidAt) {
        this.transactionId = transactionId;
        this.paidAt = paidAt;
    }
}
