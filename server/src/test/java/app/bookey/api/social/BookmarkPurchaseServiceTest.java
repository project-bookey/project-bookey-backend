package app.bookey.api.social;

import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseCheckoutRequest;
import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseVerifyRequest;
import app.bookey.api.social.payment.TossPaymentClient;
import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.wallet.BookmarkPurchase;
import app.bookey.domain.wallet.BookmarkPurchaseRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkPurchaseServiceTest {

    private static final BookeyProperties.Social SOCIAL =
            new BookeyProperties.Social(5, 16, 1, 2, 200, 50, 30, 17900);
    private static final BookeyProperties.Payment PAYMENT = new BookeyProperties.Payment(
            "bookey.plus.monthly",
            new BookeyProperties.Payment.Toss(
                    "test_ck", "test_sk", "bookey://payment/toss-success",
                    "bookey://payment/toss-fail", "bookey"),
            new BookeyProperties.Payment.Apple(
                    "issuer", "key", "app.bookey.mobile", "private-key", "SANDBOX"));

    private final WalletService walletService = mock(WalletService.class);
    private final BookmarkPurchaseRepository purchaseRepository = mock(BookmarkPurchaseRepository.class);
    private final TossPaymentClient tossPaymentClient = mock(TossPaymentClient.class);
    private final BookeyProperties properties =
            new BookeyProperties(null, null, null, null, null, null, SOCIAL, PAYMENT, null, null);
    private final BookmarkPurchaseService service =
            new BookmarkPurchaseService(walletService, purchaseRepository, properties, tossPaymentClient);

    @Test
    @DisplayName("체크아웃 — 책갈피 수량, 보너스, 금액을 담은 Toss 결제창 URL 을 만든다")
    void checkout() {
        when(purchaseRepository.save(any(BookmarkPurchase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentClient.createCheckoutUrl(any(), any())).thenReturn("https://checkout.toss.test");

        var view = service.checkout(1L, new BookmarkPurchaseCheckoutRequest(SubscriptionStore.TOSS, 10));

        assertThat(view.provider()).isEqualTo(SubscriptionStore.TOSS);
        assertThat(view.productId()).isEqualTo("bookey.bookmark.10");
        assertThat(view.orderId()).startsWith("bookey-bm-1-");
        assertThat(view.quantity()).isEqualTo(10);
        assertThat(view.bonusQuantity()).isEqualTo(1);
        assertThat(view.totalQuantity()).isEqualTo(11);
        assertThat(view.amountKrw()).isEqualTo(2000);
        assertThat(view.checkoutUrl()).isEqualTo("https://checkout.toss.test");
        assertThat(view.successUrl()).contains("kind=BOOKMARK_PURCHASE", "quantity=10");
    }

    @Test
    @DisplayName("검증 — Toss 승인 결과가 정상이면 책갈피와 보너스를 한 번 적립한다")
    void verifyToss() {
        BookmarkPurchase purchase = BookmarkPurchase.builder()
                .userId(1L)
                .provider(SubscriptionStore.TOSS)
                .productId("bookey.bookmark.10")
                .orderId("bookey-bm-1-order")
                .quantity(10)
                .bonusQuantity(1)
                .amountKrw(2000)
                .build();
        when(purchaseRepository.findByUserIdAndOrderId(1L, "bookey-bm-1-order"))
                .thenReturn(Optional.of(purchase));
        when(tossPaymentClient.confirm("test_sk", "pay_123", "bookey-bm-1-order", 2000))
                .thenReturn(new TossPaymentClient.TossPayment("pay_123", "bookey-bm-1-order", 2000, "DONE"));

        service.verify(1L, new BookmarkPurchaseVerifyRequest(
                SubscriptionStore.TOSS, "bookey.bookmark.10", "bookey-bm-1-order",
                10, 2000, "pay_123", null, null));

        verify(walletService).grantPurchasedBookmarks(1L, 11, purchase.getId());
        assertThat(purchase.isPaid()).isTrue();
    }
}
