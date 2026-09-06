package app.bookey.api.social;

import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.wallet.Subscription;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 구독 단위 테스트 (§14.2) — 활성 판정, 월 재화 지급 멱등, 관리자 지급·회수. */
class SubscriptionServiceTest {

    private static final BookeyProperties.Social SOCIAL =
            new BookeyProperties.Social(5, 16, 1, 2, 50, 30, 17900);

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final Clock clock = mock(Clock.class);
    private final BookeyProperties properties =
            new BookeyProperties(null, null, null, null, null, null, SOCIAL, null);
    private final SubscriptionService service =
            new SubscriptionService(subscriptionRepository, transactionRepository, properties, clock);

    private Subscription activeSubscription(Instant start, Instant end) {
        return Subscription.builder()
                .userId(1L).store(SubscriptionStore.ADMIN).productId("admin.grant")
                .currentPeriodStart(start).currentPeriodEnd(end)
                .build();
    }

    @Test
    @DisplayName("활성 판정 — 기간 안이면 true, 기간이 지나면 false, 구독이 없으면 false")
    void isActive() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);

        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(
                activeSubscription(now.minusSeconds(3600), now.plusSeconds(3600))));
        assertThat(service.isActive(1L)).isTrue();

        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(
                activeSubscription(now.minusSeconds(7200), now.minusSeconds(3600))));
        assertThat(service.isActive(1L)).isFalse();

        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(2L)).thenReturn(Optional.empty());
        assertThat(service.isActive(2L)).isFalse();
    }

    @Test
    @DisplayName("월 지급 — 엽서 50 · 우표 30 을 기간당 한 번만 지급한다 (멱등)")
    void monthlyGrantIsIdempotent() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Subscription sub = activeSubscription(now.minusSeconds(3600), now.plusSeconds(3600));
        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(sub));
        Wallet wallet = new Wallet(1L, LocalDate.of(2026, 9, 6));

        service.applyMonthlyGrantIfDue(1L, wallet);
        service.applyMonthlyGrantIfDue(1L, wallet);   // 같은 기간 두 번째 — 지급 없음

        assertThat(wallet.getPostcardBalance()).isEqualTo(50);
        assertThat(wallet.getStampBalance()).isEqualTo(30);
        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(WalletTransactionKind.SUBSCRIPTION_GRANT);
        assertThat(saved.getValue().getPostcardDelta()).isEqualTo(50);
        assertThat(saved.getValue().getStampDelta()).isEqualTo(30);
    }

    @Test
    @DisplayName("만료된 구독에는 지급하지 않는다")
    void noGrantWhenExpired() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Subscription sub = activeSubscription(now.minusSeconds(7200), now.minusSeconds(3600));
        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(sub));
        Wallet wallet = new Wallet(1L, LocalDate.of(2026, 9, 6));

        service.applyMonthlyGrantIfDue(1L, wallet);

        assertThat(wallet.getPostcardBalance()).isZero();
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("관리자 지급 — 없으면 새로 만들고, 있으면 지금부터 연장한다. 회수하면 즉시 비활성")
    void adminGrantAndRevoke() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.adminGrant(1L, 1);
        Subscription created = saved.getValue();
        assertThat(created.isActiveAt(now)).isTrue();
        assertThat(created.getStore()).isEqualTo(SubscriptionStore.ADMIN);

        when(subscriptionRepository.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(created));
        service.adminRevoke(1L);
        assertThat(created.isActiveAt(now.plusSeconds(1))).isFalse();
    }
}
