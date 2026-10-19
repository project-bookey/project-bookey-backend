package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelHomeFeedView;
import app.bookey.api.novel.dto.NovelDtos.NovelRefreshCheckoutRequest;
import app.bookey.api.novel.dto.NovelDtos.NovelRefreshCheckoutView;
import app.bookey.api.novel.dto.NovelDtos.NovelRefreshVerifyRequest;
import app.bookey.api.social.payment.AppStorePaymentClient;
import app.bookey.api.social.payment.GooglePlayPaymentClient;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.NovelRefreshPurchase;
import app.bookey.domain.novel.NovelRefreshPurchaseRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/**
 * 홈 열매 밭 새로고침 결제 — 오늘 무료·광고 횟수를 다 쓴 뒤 스토어 소모성 상품으로 한 번씩 산다(APPLE·GOOGLE 만).
 * 주문(checkout) → 앱이 스토어에서 결제 → 확인(verify) 순서로, 북키 책갈피 결제({@code BookmarkPurchaseService})와 같은 흐름이다.
 * 확인은 결제한 횟수를 더하기만 하고 새로고침은 하지 않는다 — 앱이 이어서 /home-feed/refresh 를 부른다.
 */
@Service @RequiredArgsConstructor
public class NovelRefreshPurchaseService {
    private final NovelRefreshPurchaseRepository purchases;
    private final NovelHomeFeedService homeFeedService;
    private final BookeyProperties properties;
    private final GooglePlayPaymentClient googlePlayPaymentClient;
    private final AppStorePaymentClient appStorePaymentClient;
    private final Clock clock;

    /** 주문을 연다. 남은 새로고침이 있으면 열지 않고, 결제를 확인할 수 없는 상태면 스토어 결제로 넘어가기 전에 거절한다. */
    @Transactional
    public NovelRefreshCheckoutView checkout(Long me, NovelRefreshCheckoutRequest request) {
        SubscriptionStore provider = request.provider();
        if (provider != SubscriptionStore.APPLE && provider != SubscriptionStore.GOOGLE) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        homeFeedService.requireRefreshesUsedUp(me);
        BookeyProperties.Novel.RefreshPurchase config = purchaseConfig();
        // 확인할 자격 증명이 없으면 여기서 막는다 — 스토어에서 돈만 받고 새로고침을 주지 못하는 일이 없게.
        if (provider == SubscriptionStore.APPLE) appleConfig();
        else googleConfig();

        NovelRefreshPurchase purchase = purchases.save(NovelRefreshPurchase.builder()
                .userId(me)
                .provider(provider)
                .productId(config.productId())
                .orderId("relaynovel-refresh-" + me + "-" + UUID.randomUUID())
                .amountKrw(config.priceKrw())
                .build());
        return new NovelRefreshCheckoutView(purchase.getProvider(), purchase.getProductId(), purchase.getOrderId(),
                purchase.getAmountKrw());
    }

    /**
     * 스토어 결제를 확인하고 결제한 새로고침 하나를 더한다. 이미 확인한 주문이면 더하지 않고 지금 상태를 돌려준다 —
     * 앱이 응답을 못 받아 다시 보내도 한 번만 더한다.
     */
    @Transactional
    public NovelHomeFeedView verify(Long me, NovelRefreshVerifyRequest request) {
        NovelRefreshPurchase purchase = purchases.lockByUserIdAndOrderId(me, request.orderId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (purchase.isPaid()) return homeFeedService.current(me);
        if (purchase.getProvider() != request.provider() || !purchase.getProductId().equals(request.productId())) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        String transactionId = switch (purchase.getProvider()) {
            case GOOGLE -> verifyGoogle(purchase, request);
            case APPLE -> verifyApple(purchase, request);
            default -> throw ApiException.of(ErrorCode.INVALID_REQUEST);
        };
        // 스토어 결제 하나로 주문 둘을 확인하지 못하게 한다. 동시에 들어온 경우는 (provider, transaction_id) 유니크가 막는다.
        if (purchases.existsByProviderAndTransactionId(purchase.getProvider(), transactionId)) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        purchase.markPaid(transactionId, clock.instant());
        return homeFeedService.addPaidRefresh(me);
    }

    /** 앱이 보낸 구매 토큰을 Play 에 다시 물어 결제됐는지(purchaseState 0) 확인한다. 토큰이 곧 이 결제의 id 다. */
    private String verifyGoogle(NovelRefreshPurchase purchase, NovelRefreshVerifyRequest request) {
        if (blank(request.receiptData())) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        GooglePlayPaymentClient.GoogleProduct product =
                googlePlayPaymentClient.getProduct(googleConfig(), purchase.getProductId(), request.receiptData());
        if (product.purchaseState() != 0) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        return request.receiptData();
    }

    /** 앱이 보낸 거래를 App Store 에 다시 물어 이 상품·릴레이노벨 앱의 거래인지 확인한다. */
    private String verifyApple(NovelRefreshPurchase purchase, NovelRefreshVerifyRequest request) {
        if (blank(request.originalTransactionId())) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        BookeyProperties.Payment.Apple apple = appleConfig();
        AppStorePaymentClient.AppStoreTransaction transaction =
                appStorePaymentClient.getTransaction(apple, request.originalTransactionId());
        if (!purchase.getProductId().equals(transaction.productId()) || !apple.bundleId().equals(transaction.bundleId())
                || blank(transaction.transactionId())) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        return transaction.transactionId();
    }

    private BookeyProperties.Payment.Apple appleConfig() {
        return NovelStoreCredentials.apple(properties.payment(), purchaseConfig().appleBundleId());
    }
    private BookeyProperties.Payment.Google googleConfig() {
        return NovelStoreCredentials.google(properties.payment(), purchaseConfig().googlePackageName());
    }

    private BookeyProperties.Novel.RefreshPurchase purchaseConfig() {
        BookeyProperties.Novel.RefreshPurchase config = properties.novel().refreshPurchase();
        if (config == null || blank(config.productId())) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        return config;
    }

    private static boolean blank(String value) { return NovelStoreCredentials.blank(value); }
}
