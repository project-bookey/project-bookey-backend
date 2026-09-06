package app.bookey.api.social;

import app.bookey.common.config.BookeyProperties;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutRequest;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutView;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionVerifyRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.wallet.Subscription;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 구독 (§14.2, 월 17,900원). MVP 는 관리자 지급(ADMIN 스토어)이 유일한 활성화 경로 —
 * 스토어 IAP 영수증 검증이 붙으면 APPLE/GOOGLE 경로가 추가된다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SubscriptionRepository subscriptionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final BookeyProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        return subscriptionRepository.findTopByUserIdOrderByIdDesc(userId)
                .map(sub -> sub.isActiveAt(clock.instant()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SubscriptionCheckoutView checkout(Long userId, SubscriptionCheckoutRequest request) {
        SubscriptionStore provider = request.provider();
        if (provider == SubscriptionStore.ADMIN) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        BookeyProperties.Payment payment = properties.payment();
        String productId = payment.subscriptionProductId();
        String orderId = "bookey-sub-" + userId + "-" + UUID.randomUUID();
        String customerKey = "bookey-user-" + userId;
        BookeyProperties.Payment.Toss toss = payment.toss();
        if (provider == SubscriptionStore.TOSS) {
            return new SubscriptionCheckoutView(
                    provider, productId, orderId, properties.social().subscriptionPriceKrw(), customerKey,
                    blankToNull(toss.clientKey()), blankToNull(toss.successUrl()), blankToNull(toss.failUrl()));
        }
        return new SubscriptionCheckoutView(
                provider, productId, orderId, properties.social().subscriptionPriceKrw(), customerKey,
                null, null, null);
    }

    /**
     * 결제 검증 계약만 먼저 고정한다. 실제 구현 시:
     * APPLE/GOOGLE 은 영수증과 originalTransactionId 를 검증하고,
     * TOSS 는 paymentKey/orderId/amount 를 승인 API 로 재검증한 뒤 grantFromPayment 로 연결한다.
     */
    @Transactional
    public void verify(Long userId, SubscriptionVerifyRequest request) {
        throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
    }

    /**
     * 이번 구독 기간의 월 재화(엽서 50 · 우표 30)를 아직 안 받았으면 지급한다 — 지갑 접근 시점에 게으르게.
     * 멱등: lastGrantPeriodStart 가 현재 기간과 같으면 재지급하지 않는다.
     */
    @Transactional
    public void applyMonthlyGrantIfDue(Long userId, Wallet wallet) {
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId).ifPresent(sub -> {
            if (!sub.isActiveAt(clock.instant()) || !sub.needsMonthlyGrant()) {
                return;
            }
            BookeyProperties.Social social = properties.social();
            wallet.add(0, social.subscriptionMonthlyPostcards(), social.subscriptionMonthlyStamps());
            transactionRepository.save(WalletTransaction.builder()
                    .userId(userId)
                    .kind(WalletTransactionKind.SUBSCRIPTION_GRANT)
                    .postcardDelta(social.subscriptionMonthlyPostcards())
                    .stampDelta(social.subscriptionMonthlyStamps())
                    .refType("SUBSCRIPTION").refId(sub.getId())
                    .build());
            sub.markGranted();
        });
    }

    /** 관리자 지급 — 기존 구독이 있으면 지금부터 months 개월로 연장한다. */
    @Transactional
    public void adminGrant(Long userId, int months) {
        Instant now = clock.instant();
        Instant end = ZonedDateTime.ofInstant(now, KST).plusMonths(months).toInstant();
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId).ifPresentOrElse(
                sub -> sub.extend(now, end),
                () -> subscriptionRepository.save(Subscription.builder()
                        .userId(userId)
                        .store(SubscriptionStore.ADMIN)
                        .productId("admin.grant")
                        .currentPeriodStart(now)
                        .currentPeriodEnd(end)
                        .build()));
    }

    @Transactional
    public void adminRevoke(Long userId) {
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId)
                .ifPresent(sub -> sub.cancel(clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
