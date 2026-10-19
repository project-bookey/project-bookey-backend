package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.api.social.payment.AppStorePaymentClient;
import app.bookey.api.social.payment.GooglePlayPaymentClient;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import app.bookey.domain.wallet.SubscriptionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * 햇살 충전 — 주문을 만들고(checkout) 앱이 스토어에서 결제한 뒤, 서버가 스토어에 그 거래를 확인하면(verify) 햇살을 넣는다.
 * 새로고침 결제(NovelRefreshPurchaseService)와 같은 흐름이고 앱 안 결제(APPLE·GOOGLE)만 받는다.
 */
@Service @RequiredArgsConstructor
public class NovelSunlightPurchaseService {
    private final NovelSunlightPurchaseRepository purchases;
    private final NovelSunlightService sunlight;
    private final BookeyProperties properties;
    private final GooglePlayPaymentClient googlePlay;
    private final AppStorePaymentClient appStore;

    /** 결제를 확인할 수 없는 상태면 스토어 결제로 넘어가기 전에 거절한다 — 돈만 받고 햇살을 주지 못하는 일이 없게. */
    @Transactional
    public NovelSunlightCheckoutView checkout(Long me, NovelSunlightCheckoutRequest request) {
        SubscriptionStore provider = request.provider();
        if (provider != SubscriptionStore.APPLE && provider != SubscriptionStore.GOOGLE)
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        if (provider == SubscriptionStore.APPLE) appleConfig();
        else googleConfig();
        BookeyProperties.Novel.Sunlight.Product product = product(request.productId());
        NovelSunlightPurchase purchase = purchases.save(new NovelSunlightPurchase(me, provider, product.productId(),
                "relaynovel-sunlight-" + me + "-" + UUID.randomUUID(), product.quantity(), product.amountKrw()));
        return new NovelSunlightCheckoutView(purchase.getProvider(), purchase.getProductId(), purchase.getOrderId(),
                purchase.getQuantity(), purchase.getAmountKrw());
    }

    /** 같은 주문을 두 번 확인해도 햇살은 한 번만 들어간다 — 앱이 응답을 못 받아 다시 보내도 된다. */
    @Transactional
    public NovelSunlightView verify(Long me, NovelSunlightVerifyRequest request) {
        NovelSunlightPurchase purchase = purchases.lockByUserIdAndOrderId(me, request.orderId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (purchase.isPaid()) return sunlight.view(me);
        if (purchase.getProvider() != request.provider() || !purchase.getProductId().equals(request.productId()))
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        String transactionId = switch (purchase.getProvider()) {
            case GOOGLE -> verifyGoogle(purchase, request);
            case APPLE -> verifyApple(purchase, request);
            default -> throw ApiException.of(ErrorCode.INVALID_REQUEST);
        };
        // 스토어 결제 하나로 주문 둘을 채우지 못하게 한다. 동시에 들어온 경우는 transaction_id 유니크가 막는다.
        if (purchases.existsByTransactionId(transactionId)) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        purchase.markPaid(transactionId);
        int balance = sunlight.grant(me, purchase.getQuantity(), NovelSunlightTransactionKind.PURCHASE,
                "NOVEL_SUNLIGHT_PURCHASE", purchase.getId());
        return sunlight.view(balance);
    }

    /** 앱이 보낸 구매 토큰을 Play 에 다시 물어 결제됐는지(purchaseState 0) 확인한다. 토큰이 곧 이 결제의 id 다. */
    private String verifyGoogle(NovelSunlightPurchase purchase, NovelSunlightVerifyRequest request) {
        if (NovelStoreCredentials.blank(request.receiptData())) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        GooglePlayPaymentClient.GoogleProduct paid = googlePlay.getProduct(googleConfig(), purchase.getProductId(),
                request.receiptData());
        if (paid.purchaseState() != 0) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        return request.receiptData();
    }

    /** 앱이 보낸 거래를 App Store 에 다시 물어 이 상품·릴레이노벨 앱의 거래인지 확인한다. */
    private String verifyApple(NovelSunlightPurchase purchase, NovelSunlightVerifyRequest request) {
        if (NovelStoreCredentials.blank(request.originalTransactionId())) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        BookeyProperties.Payment.Apple apple = appleConfig();
        AppStorePaymentClient.AppStoreTransaction paid = appStore.getTransaction(apple, request.originalTransactionId());
        if (!purchase.getProductId().equals(paid.productId()) || !apple.bundleId().equals(paid.bundleId())
                || NovelStoreCredentials.blank(paid.transactionId()))
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        return paid.transactionId();
    }

    private BookeyProperties.Payment.Apple appleConfig() {
        return NovelStoreCredentials.apple(properties.payment(), config().appleBundleId());
    }
    private BookeyProperties.Payment.Google googleConfig() {
        return NovelStoreCredentials.google(properties.payment(), config().googlePackageName());
    }
    private BookeyProperties.Novel.Sunlight.Product product(String productId) {
        return config().products().stream().filter(p -> p.productId().equals(productId)).findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_REQUEST));
    }
    private BookeyProperties.Novel.Sunlight config() {
        BookeyProperties.Novel.Sunlight config = properties.novel().sunlight();
        if (config == null) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        return config;
    }
}
