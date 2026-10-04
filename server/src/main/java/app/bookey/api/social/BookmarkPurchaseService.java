package app.bookey.api.social;

import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseCheckoutRequest;
import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseCheckoutView;
import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseVerifyRequest;
import app.bookey.api.social.dto.SocialDtos.WalletView;
import app.bookey.api.social.payment.TossPaymentClient;
import app.bookey.api.social.payment.TossPaymentClient.TossCreatePaymentRequest;
import app.bookey.api.social.payment.GooglePlayPaymentClient;
import app.bookey.api.social.payment.AppStorePaymentClient;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.wallet.BookmarkPurchase;
import app.bookey.domain.wallet.BookmarkPurchaseRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookmarkPurchaseService {

    private static final Set<Integer> STORE_QUANTITIES = Set.of(5, 10, 50);

    private final WalletService walletService;
    private final BookmarkPurchaseRepository purchaseRepository;
    private final BookeyProperties properties;
    private final TossPaymentClient tossPaymentClient;
    private final GooglePlayPaymentClient googlePlayPaymentClient;
    private final AppStorePaymentClient appStorePaymentClient;

    @Transactional
    public BookmarkPurchaseCheckoutView checkout(Long userId, BookmarkPurchaseCheckoutRequest request) {
        if (request.provider() == SubscriptionStore.ADMIN) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        int quantity = request.quantity();
        if (request.provider() != SubscriptionStore.TOSS && !STORE_QUANTITIES.contains(quantity)) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        int bonus = bonusFor(quantity);
        int amountKrw = amountKrw(quantity);
        String productId = productId(quantity);
        String orderId = "bookey-bm-" + userId + "-" + UUID.randomUUID();
        String customerKey = "bookey-user-" + userId;
        BookmarkPurchase purchase = purchaseRepository.save(BookmarkPurchase.builder()
                .userId(userId)
                .provider(request.provider())
                .productId(productId)
                .orderId(orderId)
                .quantity(quantity)
                .bonusQuantity(bonus)
                .amountKrw(amountKrw)
                .build());
        BookeyProperties.Payment.Toss toss = properties.payment().toss();
        if (request.provider() == SubscriptionStore.TOSS) {
            requireTossCheckoutConfigured(toss);
            String successUrl = appendQuery(toss.successUrl(),
                    "kind=BOOKMARK_PURCHASE",
                    "provider=TOSS",
                    "productId=" + productId,
                    "quantity=" + quantity);
            String failUrl = appendQuery(toss.failUrl(), "kind=BOOKMARK_PURCHASE");
            String checkoutUrl = tossPaymentClient.createCheckoutUrl(toss.secretKey(), new TossCreatePaymentRequest(
                    orderId, "BOOKEY 책갈피 " + quantity + "개", amountKrw, customerKey,
                    successUrl, failUrl, toss.appScheme()));
            return view(purchase.getProvider(), purchase.getProductId(), purchase.getOrderId(),
                    purchase.getQuantity(), purchase.getBonusQuantity(), purchase.getAmountKrw(), customerKey,
                    checkoutUrl, blankToNull(toss.clientKey()), successUrl, failUrl);
        }
        return view(purchase.getProvider(), purchase.getProductId(), purchase.getOrderId(),
                purchase.getQuantity(), purchase.getBonusQuantity(), purchase.getAmountKrw(), customerKey,
                null, null, null, null);
    }

    @Transactional
    public WalletView verify(Long userId, BookmarkPurchaseVerifyRequest request) {
        BookmarkPurchase purchase = purchaseRepository.findByUserIdAndOrderId(userId, request.orderId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (purchase.isPaid()) {
            return walletService.view(userId);
        }
        if (purchase.getProvider() != request.provider()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        if (!purchase.getProductId().equals(request.productId())
                || purchase.getQuantity() != request.quantity()
                || purchase.getAmountKrw() != request.amountKrw()) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        String transactionId;
        if (request.provider() == SubscriptionStore.TOSS) {
            if (request.paymentKey() == null || request.paymentKey().isBlank()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
            TossPaymentClient.TossPayment payment = tossPaymentClient.confirm(
                    requireConfigured(properties.payment().toss().secretKey()),
                    request.paymentKey(), purchase.getOrderId(), purchase.getAmountKrw());
            if (!request.paymentKey().equals(payment.paymentKey()) || !purchase.getOrderId().equals(payment.orderId())
                    || payment.totalAmount() != purchase.getAmountKrw() || !"DONE".equals(payment.status()))
                throw ApiException.of(ErrorCode.INVALID_REQUEST);
            transactionId = payment.paymentKey();
        } else if (request.provider() == SubscriptionStore.GOOGLE) {
            if (request.receiptData() == null || request.receiptData().isBlank()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
            GooglePlayPaymentClient.GoogleProduct product = googlePlayPaymentClient.getProduct(
                    properties.payment().google(), request.productId(), request.receiptData());
            if (product.purchaseState() != 0) throw ApiException.of(ErrorCode.INVALID_REQUEST);
            transactionId = request.receiptData();
        } else if (request.provider() == SubscriptionStore.APPLE) {
            if (request.originalTransactionId() == null || request.originalTransactionId().isBlank()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
            AppStorePaymentClient.AppStoreTransaction transaction = appStorePaymentClient.getTransaction(
                    properties.payment().apple(), request.originalTransactionId());
            if (!request.productId().equals(transaction.productId())
                    || !properties.payment().apple().bundleId().equals(transaction.bundleId()))
                throw ApiException.of(ErrorCode.INVALID_REQUEST);
            transactionId = transaction.transactionId();
        } else {
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        purchase.markPaid(transactionId);
        return walletService.grantPurchasedBookmarks(userId, purchase.totalQuantity(), purchase.getId());
    }

    private BookmarkPurchaseCheckoutView view(SubscriptionStore provider, String productId, String orderId,
                                              int quantity, int bonus, int amountKrw, String customerKey,
                                              String checkoutUrl, String tossClientKey,
                                              String successUrl, String failUrl) {
        return new BookmarkPurchaseCheckoutView(provider, productId, orderId, quantity, bonus, quantity + bonus,
                amountKrw, customerKey, checkoutUrl, tossClientKey, successUrl, failUrl);
    }

    private int amountKrw(int quantity) {
        return quantity * properties.social().bookmarkPriceKrw();
    }

    private static int bonusFor(int quantity) {
        return quantity >= 10 ? quantity / 10 : 0;
    }

    private static String productId(int quantity) {
        return "bookey.bookmark." + quantity;
    }

    private void requireTossCheckoutConfigured(BookeyProperties.Payment.Toss toss) {
        requireConfigured(toss.secretKey());
        requireConfigured(toss.successUrl());
        requireConfigured(toss.failUrl());
        requireConfigured(toss.appScheme());
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
