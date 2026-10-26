package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.api.social.payment.AppStorePaymentClient;
import app.bookey.api.social.payment.AppStorePaymentClient.AppStoreTransaction;
import app.bookey.api.social.payment.GooglePlayPaymentClient;
import app.bookey.api.social.payment.GooglePlayPaymentClient.GoogleProduct;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelRefreshPurchaseServiceTest {
    private static final Long ME = 1L;
    private static final Instant NOON = Instant.parse("2026-10-15T03:00:00Z");          // KST 10-15 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final String PRODUCT = "relaynovel.refresh.1";
    private static final String RELAY_APP = "app.relaynovel.mobile";
    private static final BookeyProperties.Payment PAYMENT = new BookeyProperties.Payment("bookey.plus.monthly", null,
            new BookeyProperties.Payment.Apple("issuer", "key", "app.bookey.mobile", "private-key", "SANDBOX"),
            new BookeyProperties.Payment.Google("app.bookey.mobile", "service@test", "private-key", "rtdn-token"));
    private static final BookeyProperties.Novel NOVEL = new BookeyProperties.Novel(2, 3, 7,
            new BookeyProperties.Novel.RefreshPurchase(PRODUCT, 1000, RELAY_APP, RELAY_APP), null);

    private final NovelHomeFeedRepository feeds = mock(NovelHomeFeedRepository.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelRefreshPurchaseRepository purchases = mock(NovelRefreshPurchaseRepository.class);
    private final GooglePlayPaymentClient google = mock(GooglePlayPaymentClient.class);
    private final AppStorePaymentClient apple = mock(AppStorePaymentClient.class);
    private final Clock clock = mock(Clock.class);
    private final NovelService novelService = new NovelService(novels, mock(NovelMemberRepository.class),
            chapters, mock(NovelDraftRepository.class), mock(NovelCoverRepository.class),
            mock(NovelLikeRepository.class), mock(UserRepository.class), mock(RateLimiter.class), deliveries,
            mock(NovelDeliveryService.class), mock(NovelBaseWorkRepository.class), mock(UserFollowRepository.class), mock(NovelSunlightService.class),
            mock(NovelFavoriteRepository.class));
    private final NovelHomeFeedService homeFeedService = feedService(PAYMENT);
    private final NovelRefreshPurchaseService service = purchaseService(homeFeedService, PAYMENT);

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(NOON);
        when(purchases.save(any(NovelRefreshPurchase.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test @DisplayName("무료·광고·결제한 새로고침 중 하나라도 남아 있으면 결제 주문을 열지 않는다")
    void checkoutRejectedWhileRefreshesLeft() {
        int[][] stillLeft = {{1, 3, 0}, {2, 2, 0}, {2, 3, 1}};   // 무료 쓴 수, 광고 쓴 수, 결제한 수
        for (int[] counts : stillLeft) {
            storedFeed(counts[0], counts[1], counts[2]);

            assertThatThrownBy(() -> service.checkout(ME, new NovelRefreshCheckoutRequest(SubscriptionStore.GOOGLE)))
                    .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_REFRESH_STILL_LEFT);
        }
        assertThat(ErrorCode.NOVEL_REFRESH_STILL_LEFT.getStatus().value()).isEqualTo(409);
        verify(purchases, never()).save(any());
    }

    @Test @DisplayName("오늘 무료·광고 횟수를 다 썼고 결제한 횟수도 없으면 릴레이노벨 새로고침 상품으로 주문을 연다")
    void checkoutOpensOrderWhenAllUsedUp() {
        storedFeed(2, 3, 0);

        NovelRefreshCheckoutView view = service.checkout(ME, new NovelRefreshCheckoutRequest(SubscriptionStore.APPLE));

        assertThat(view.provider()).isEqualTo(SubscriptionStore.APPLE);
        assertThat(view.productId()).isEqualTo(PRODUCT);
        assertThat(view.orderId()).startsWith("relaynovel-refresh-1-");
        assertThat(view.amountKrw()).isEqualTo(1000);
        ArgumentCaptor<NovelRefreshPurchase> saved = ArgumentCaptor.forClass(NovelRefreshPurchase.class);
        verify(purchases).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ME);
        assertThat(saved.getValue().getOrderId()).isEqualTo(view.orderId());
        assertThat(saved.getValue().isPaid()).isFalse();
    }

    @Test @DisplayName("결제 주문은 APPLE·GOOGLE 만 받는다")
    void checkoutOnlyStores() {
        storedFeed(2, 3, 0);

        for (SubscriptionStore other : List.of(SubscriptionStore.TOSS, SubscriptionStore.ADMIN)) {
            assertThatThrownBy(() -> service.checkout(ME, new NovelRefreshCheckoutRequest(other)))
                    .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }
        verify(purchases, never()).save(any());
    }

    @Test @DisplayName("결제를 확인할 자격 증명이 없으면 스토어 결제로 넘어가기 전에 PAYMENT_NOT_CONFIGURED 로 막는다")
    void checkoutRejectedWithoutStoreCredentials() {
        storedFeed(2, 3, 0);
        BookeyProperties.Payment noKeys = new BookeyProperties.Payment("bookey.plus.monthly", null,
                new BookeyProperties.Payment.Apple("", "", "app.bookey.mobile", "", "SANDBOX"),
                new BookeyProperties.Payment.Google("app.bookey.mobile", "", "", ""));
        NovelRefreshPurchaseService unconfigured = purchaseService(feedService(noKeys), noKeys);

        for (SubscriptionStore store : List.of(SubscriptionStore.APPLE, SubscriptionStore.GOOGLE)) {
            assertThatThrownBy(() -> unconfigured.checkout(ME, new NovelRefreshCheckoutRequest(store)))
                    .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        verify(purchases, never()).save(any());
    }

    @Test @DisplayName("Google 결제를 릴레이노벨 패키지로 확인하면 결제한 새로고침 하나를 더하고, 새로고침은 하지 않는다")
    void verifyGoogleAddsOnePaidRefresh() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        NovelRefreshPurchase purchase = order(SubscriptionStore.GOOGLE);
        ArgumentCaptor<BookeyProperties.Payment.Google> config = ArgumentCaptor.forClass(BookeyProperties.Payment.Google.class);
        when(google.getProduct(config.capture(), eq(PRODUCT), eq("purchase-token"))).thenReturn(new GoogleProduct(0, "GPA.1"));

        NovelHomeFeedView view = service.verify(ME, googleVerify(PRODUCT, "purchase-token"));

        assertThat(config.getValue().packageName()).isEqualTo(RELAY_APP);
        assertThat(config.getValue().serviceAccountEmail()).isEqualTo("service@test");
        assertThat(view.paidRefreshes()).isEqualTo(1);
        assertThat(view.freeRefreshesLeftToday()).isZero();
        assertThat(view.adRefreshesLeftToday()).isZero();
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        assertThat(feed.getChapterIds()).containsExactly(1L);
        assertThat(purchase.isPaid()).isTrue();
        assertThat(purchase.getTransactionId()).isEqualTo("purchase-token");
        assertThat(purchase.getPaidAt()).isEqualTo(NOON);
        verify(chapters, never()).pickHomeFruits(any(), any(), any(), any(), anyInt());
    }

    @Test @DisplayName("Apple 결제를 릴레이노벨 번들로 확인하면 결제한 새로고침 하나를 더한다")
    void verifyAppleAddsOnePaidRefresh() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        NovelRefreshPurchase purchase = order(SubscriptionStore.APPLE);
        ArgumentCaptor<BookeyProperties.Payment.Apple> config = ArgumentCaptor.forClass(BookeyProperties.Payment.Apple.class);
        when(apple.getTransaction(config.capture(), eq("2000001")))
                .thenReturn(new AppStoreTransaction("2000001", "2000001", PRODUCT, RELAY_APP, 0L));

        NovelHomeFeedView view = service.verify(ME, appleVerify("2000001"));

        assertThat(config.getValue().bundleId()).isEqualTo(RELAY_APP);
        assertThat(config.getValue().issuerId()).isEqualTo("issuer");
        assertThat(config.getValue().keyId()).isEqualTo("key");
        assertThat(view.paidRefreshes()).isEqualTo(1);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        assertThat(purchase.getTransactionId()).isEqualTo("2000001");
    }

    @Test @DisplayName("다른 앱(북키) 번들의 Apple 거래로는 확인하지 않는다")
    void verifyAppleRejectsOtherBundle() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        NovelRefreshPurchase purchase = order(SubscriptionStore.APPLE);
        when(apple.getTransaction(any(), eq("2000001")))
                .thenReturn(new AppStoreTransaction("2000001", "2000001", PRODUCT, "app.bookey.mobile", 0L));

        assertThatThrownBy(() -> service.verify(ME, appleVerify("2000001")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(purchase.isPaid()).isFalse();
        assertThat(feed.getPaidRefreshes()).isZero();
    }

    @Test @DisplayName("이미 확인한 주문을 다시 확인하면 스토어에 묻지 않고 더하지도 않은 채 지금 상태를 돌려준다")
    void verifyIsIdempotent() {
        NovelHomeFeed feed = storedFeed(2, 3, 1);
        NovelRefreshPurchase purchase = order(SubscriptionStore.GOOGLE);
        purchase.markPaid("purchase-token", NOON.minusSeconds(5));

        NovelHomeFeedView view = service.verify(ME, googleVerify(PRODUCT, "purchase-token"));

        assertThat(view.paidRefreshes()).isEqualTo(1);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        verifyNoInteractions(google, apple);
    }

    @Test @DisplayName("다른 주문에 이미 쓴 스토어 결제로는 확인하지 않는다")
    void verifyRejectsReusedTransaction() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        NovelRefreshPurchase purchase = order(SubscriptionStore.GOOGLE);
        when(google.getProduct(any(), eq(PRODUCT), eq("used-token"))).thenReturn(new GoogleProduct(0, "GPA.1"));
        when(purchases.existsByProviderAndTransactionId(SubscriptionStore.GOOGLE, "used-token")).thenReturn(true);

        assertThatThrownBy(() -> service.verify(ME, googleVerify(PRODUCT, "used-token")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(purchase.isPaid()).isFalse();
        assertThat(feed.getPaidRefreshes()).isZero();
    }

    @Test @DisplayName("주문과 상품이나 스토어가 다르면 스토어에 묻기 전에 거절한다")
    void verifyRejectsMismatchedOrder() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        order(SubscriptionStore.GOOGLE);

        assertThatThrownBy(() -> service.verify(ME, googleVerify("relaynovel.refresh.10", "purchase-token")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.verify(ME, appleVerify("2000001")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(feed.getPaidRefreshes()).isZero();
        verifyNoInteractions(google, apple);
    }

    @Test @DisplayName("Google 구매 토큰이 없거나 결제 완료 상태가 아니면 확인하지 않는다")
    void verifyGoogleRejectsMissingOrUnpaidToken() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        NovelRefreshPurchase purchase = order(SubscriptionStore.GOOGLE);
        when(google.getProduct(any(), eq(PRODUCT), eq("pending-token"))).thenReturn(new GoogleProduct(2, "GPA.2"));

        assertThatThrownBy(() -> service.verify(ME, googleVerify(PRODUCT, " ")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.verify(ME, googleVerify(PRODUCT, "pending-token")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(purchase.isPaid()).isFalse();
        assertThat(feed.getPaidRefreshes()).isZero();
    }

    @Test @DisplayName("내 주문이 아니거나 없는 주문이면 NOT_FOUND")
    void verifyUnknownOrder() {
        when(purchases.lockByUserIdAndOrderId(ME, "relaynovel-refresh-2-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(ME, new NovelRefreshVerifyRequest(SubscriptionStore.GOOGLE,
                "relaynovel-refresh-2-x", PRODUCT, "purchase-token", null)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test @DisplayName("결제를 확인한 뒤 새로고침이 새 열매가 없어 거절되면 결제한 횟수는 다음에 쓰게 남는다")
    void paidRefreshSurvivesRefreshWithoutNewNovels() {
        NovelHomeFeed feed = storedFeed(2, 3, 0);
        order(SubscriptionStore.GOOGLE);
        when(google.getProduct(any(), eq(PRODUCT), eq("purchase-token"))).thenReturn(new GoogleProduct(0, "GPA.1"));
        service.verify(ME, googleVerify(PRODUCT, "purchase-token"));
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(), List.of(11L));

        assertThatThrownBy(() -> homeFeedService.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_FEED_NO_NEW);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        assertThat(homeFeedService.refresh(ME).paidRefreshes()).isZero();
    }

    private NovelHomeFeedService feedService(BookeyProperties.Payment payment) {
        return new NovelHomeFeedService(feeds, chapters, novelService, properties(payment), clock);
    }
    private NovelRefreshPurchaseService purchaseService(NovelHomeFeedService feedService, BookeyProperties.Payment payment) {
        return new NovelRefreshPurchaseService(purchases, feedService, properties(payment), google, apple, clock);
    }
    private static BookeyProperties properties(BookeyProperties.Payment payment) {
        return new BookeyProperties(null, null, null, null, null, null, null, payment, null, null, NOVEL);
    }
    /** 열매 하나가 놓인 피드 자리. 열매 모양은 이 테스트의 관심 밖이라 이어 쓸 수 있는 열매가 없는 것으로 둔다. */
    private NovelHomeFeed storedFeed(int freeUsed, int adUsed, int paid) {
        NovelHomeFeed feed = new NovelHomeFeed(ME, TODAY);
        feed.placeFirst(List.of(1L));
        ReflectionTestUtils.setField(feed, "freeUsedToday", (short) freeUsed);
        ReflectionTestUtils.setField(feed, "adUsedToday", (short) adUsed);
        ReflectionTestUtils.setField(feed, "paidRefreshes", paid);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.of(feed));
        when(chapters.continuableAmong(eq(ME), anyCollection())).thenReturn(List.of());
        return feed;
    }
    private NovelRefreshPurchase order(SubscriptionStore provider) {
        NovelRefreshPurchase purchase = NovelRefreshPurchase.builder()
                .userId(ME).provider(provider).productId(PRODUCT).orderId("relaynovel-refresh-1-abc").amountKrw(1000).build();
        when(purchases.lockByUserIdAndOrderId(ME, "relaynovel-refresh-1-abc")).thenReturn(Optional.of(purchase));
        return purchase;
    }
    private static NovelRefreshVerifyRequest googleVerify(String productId, String token) {
        return new NovelRefreshVerifyRequest(SubscriptionStore.GOOGLE, "relaynovel-refresh-1-abc", productId, token, null);
    }
    private static NovelRefreshVerifyRequest appleVerify(String transactionId) {
        return new NovelRefreshVerifyRequest(SubscriptionStore.APPLE, "relaynovel-refresh-1-abc", PRODUCT, null, transactionId);
    }
}
