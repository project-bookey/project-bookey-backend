package app.bookey.api.social;

import app.bookey.common.config.BookeyProperties;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutRequest;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutView;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionVerifyRequest;
import app.bookey.api.social.payment.AppStorePaymentClient;
import app.bookey.api.social.payment.TossPaymentClient;
import app.bookey.api.social.payment.TossPaymentClient.TossCreatePaymentRequest;
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
    private final TossPaymentClient tossPaymentClient;
    private final AppStorePaymentClient appStorePaymentClient;

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
            requireTossCheckoutConfigured(toss);
            String successUrl = appendQuery(toss.successUrl(), "provider=TOSS", "productId=" + productId);
            String failUrl = appendQuery(toss.failUrl(), "provider=TOSS", "productId=" + productId);
            String checkoutUrl = tossPaymentClient.createCheckoutUrl(toss.secretKey(), new TossCreatePaymentRequest(
                    orderId, "BOOKEY PLUS 월 구독", properties.social().subscriptionPriceKrw(), customerKey,
                    successUrl, failUrl, toss.appScheme()));
            return new SubscriptionCheckoutView(
                    provider, productId, orderId, properties.social().subscriptionPriceKrw(), customerKey,
                    checkoutUrl, blankToNull(toss.clientKey()), successUrl, failUrl);
        }
        return new SubscriptionCheckoutView(
                provider, productId, orderId, properties.social().subscriptionPriceKrw(), customerKey,
                null, null, null, null);
    }

    /**
     * 결제 검증. 클라이언트 값은 결과 통지로만 보고, Toss/App Store 서버 API 로 다시 조회한 결과만 신뢰한다.
     * GOOGLE 은 Play Billing 연결 전까지 막아 둔다.
     */
    @Transactional
    public void verify(Long userId, SubscriptionVerifyRequest request) {
        if (!properties.payment().subscriptionProductId().equals(request.productId())) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        if (request.provider() == SubscriptionStore.TOSS) {
            verifyToss(userId, request);
            return;
        }
        if (request.provider() == SubscriptionStore.APPLE) {
            verifyApple(userId, request);
            return;
        }
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

    private void verifyToss(Long userId, SubscriptionVerifyRequest request) {
        if (request.paymentKey() == null || request.orderId() == null || request.amountKrw() == null) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        if (!request.orderId().startsWith("bookey-sub-" + userId + "-")) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        int price = properties.social().subscriptionPriceKrw();
        if (request.amountKrw() != price) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        String secretKey = requireConfigured(properties.payment().toss().secretKey());
        TossPaymentClient.TossPayment payment =
                tossPaymentClient.confirm(secretKey, request.paymentKey(), request.orderId(), price);
        if (!request.paymentKey().equals(payment.paymentKey())
                || !request.orderId().equals(payment.orderId())
                || payment.totalAmount() != price
                || !"DONE".equals(payment.status())) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        activateFromPayment(userId, SubscriptionStore.TOSS, request.productId(), payment.paymentKey(),
                clock.instant().plusSeconds(31L * 24 * 60 * 60));
    }

    private void verifyApple(Long userId, SubscriptionVerifyRequest request) {
        String transactionId = request.originalTransactionId();
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = request.paymentKey();
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        BookeyProperties.Payment.Apple apple = properties.payment().apple();
        requireAppleConfigured(apple);
        AppStorePaymentClient.AppStoreTransaction transaction =
                appStorePaymentClient.getTransaction(apple, transactionId);
        if (!request.productId().equals(transaction.productId())
                || !apple.bundleId().equals(transaction.bundleId())
                || transaction.expiresDateMillis() <= clock.instant().toEpochMilli()) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        String original = transaction.originalTransactionId().isBlank()
                ? transaction.transactionId()
                : transaction.originalTransactionId();
        activateFromPayment(userId, SubscriptionStore.APPLE, request.productId(), original,
                Instant.ofEpochMilli(transaction.expiresDateMillis()));
    }

    private void activateFromPayment(Long userId, SubscriptionStore store, String productId,
                                     String originalTransactionId, Instant periodEnd) {
        if (subscriptionRepository.existsByStoreAndOriginalTransactionId(store, originalTransactionId)) {
            return;
        }
        Instant now = clock.instant();
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .store(store)
                .productId(productId)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .originalTransactionId(originalTransactionId)
                .build());
    }

    private void requireTossCheckoutConfigured(BookeyProperties.Payment.Toss toss) {
        requireConfigured(toss.secretKey());
        requireConfigured(toss.successUrl());
        requireConfigured(toss.failUrl());
        requireConfigured(toss.appScheme());
    }

    private void requireAppleConfigured(BookeyProperties.Payment.Apple apple) {
        requireConfigured(apple.issuerId());
        requireConfigured(apple.keyId());
        requireConfigured(apple.bundleId());
        requireConfigured(apple.privateKey());
    }

    private static String requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        return value;
    }

    private static String appendQuery(String url, String... params) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + String.join("&", params);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
