package app.bookey.domain.wallet;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 구독 (§14.2, 월 17,900원). 현재 기간과 월 재화 지급 멱등 키를 가진다. */
@Getter
@Entity
@Table(name = "subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionStore store;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    /** 이 기간에 월 재화를 지급했는가 — currentPeriodStart 와 같으면 지급 완료. */
    @Column(name = "last_grant_period_start")
    private Instant lastGrantPeriodStart;

    @Column(name = "original_transaction_id", length = 200)
    private String originalTransactionId;

    @Builder
    private Subscription(Long userId, SubscriptionStore store, String productId,
                         Instant currentPeriodStart, Instant currentPeriodEnd,
                         String originalTransactionId) {
        this.userId = userId;
        this.store = store;
        this.productId = productId;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.originalTransactionId = originalTransactionId;
    }

    public boolean isActiveAt(Instant now) {
        return status == SubscriptionStatus.ACTIVE && now.isBefore(currentPeriodEnd);
    }

    public boolean needsMonthlyGrant() {
        return !currentPeriodStart.equals(lastGrantPeriodStart);
    }

    public void markGranted() {
        this.lastGrantPeriodStart = currentPeriodStart;
    }

    public void extend(Instant newPeriodStart, Instant newPeriodEnd) {
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = newPeriodStart;
        this.currentPeriodEnd = newPeriodEnd;
    }

    public void cancel(Instant now) {
        this.status = SubscriptionStatus.CANCELLED;
        if (currentPeriodEnd.isAfter(now)) {
            this.currentPeriodEnd = now;
        }
    }
}
