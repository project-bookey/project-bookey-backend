package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.ExchangeRequest;
import app.bookey.api.social.dto.SocialDtos.ExchangeTarget;
import app.bookey.api.social.dto.SocialDtos.WalletView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletRepository;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지갑 정책 단위 테스트 — 무료 엽서 KST 자정 리셋(§14.9 확정), 책갈피 교환 환율, 지불 우선순위.
 */
class WalletServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BookeyProperties.Social SOCIAL =
            new BookeyProperties.Social(5, 16, 1, 2, 50, 30, 17900);

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final Clock clock = mock(Clock.class);
    private final BookeyProperties properties =
            new BookeyProperties(null, null, null, null, null, null, SOCIAL, null);
    private final WalletService service =
            new WalletService(walletRepository, transactionRepository, subscriptionService, properties, clock);

    private Wallet wallet(Instant now) {
        Wallet wallet = new Wallet(1L, LocalDate.ofInstant(now, KST));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        return wallet;
    }

    @Test
    @DisplayName("무료 엽서는 KST 자정에 리셋된다 — 23:59 에 5장을 다 써도 00:01 이면 다시 5장")
    void freePostcardsResetAtKstMidnight() {
        Instant beforeMidnight = Instant.parse("2026-09-06T14:59:00Z"); // KST 2026-09-06 23:59
        Instant afterMidnight = Instant.parse("2026-09-06T15:01:00Z");  // KST 2026-09-07 00:01
        when(clock.instant()).thenReturn(beforeMidnight);
        Wallet wallet = wallet(beforeMidnight);

        for (int i = 0; i < 5; i++) {
            service.payPostcardSend(1L, service.prepared(1L), null);
        }
        assertThat(wallet.freePostcardsLeft(5)).isZero();
        assertThatThrownBy(() -> service.payPostcardSend(1L, service.prepared(1L), null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_POSTCARD);

        // KST 자정을 넘기면 무료 5장이 돌아온다
        when(clock.instant()).thenReturn(afterMidnight);
        Wallet prepared = service.prepared(1L);
        assertThat(prepared.freePostcardsLeft(5)).isEqualTo(5);
        service.payPostcardSend(1L, prepared, null);
        assertThat(prepared.freePostcardsLeft(5)).isEqualTo(4);
    }

    @Test
    @DisplayName("발송 지불 우선순위 — 무료 일일분 먼저, 소진되면 보유 엽서를 차감하고 원장에 남긴다")
    void paymentOrderFreeThenBalance() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Wallet wallet = wallet(now);
        wallet.add(0, 2, 0); // 보유 엽서 2장

        for (int i = 0; i < 5; i++) {
            service.payPostcardSend(1L, service.prepared(1L), 10L);
        }
        assertThat(wallet.getPostcardBalance()).isEqualTo(2); // 무료분을 먼저 썼다

        service.payPostcardSend(1L, service.prepared(1L), 11L);
        assertThat(wallet.getPostcardBalance()).isEqualTo(1); // 이제 보유분 차감

        ArgumentCaptor<WalletTransaction> saved = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository, times(6)).save(saved.capture());
        List<WalletTransactionKind> kinds = saved.getAllValues().stream()
                .map(WalletTransaction::getKind).toList();
        assertThat(kinds).containsExactly(
                WalletTransactionKind.SEND_POSTCARD_FREE, WalletTransactionKind.SEND_POSTCARD_FREE,
                WalletTransactionKind.SEND_POSTCARD_FREE, WalletTransactionKind.SEND_POSTCARD_FREE,
                WalletTransactionKind.SEND_POSTCARD_FREE, WalletTransactionKind.SEND_POSTCARD);
        assertThat(saved.getAllValues().get(5).getPostcardDelta()).isEqualTo(-1);
    }

    @Test
    @DisplayName("교환 환율 — 엽서 1장 = 책갈피 1, 우표 1개 = 책갈피 2. 부족하면 INSUFFICIENT_BOOKMARK")
    void exchangeRates() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Wallet wallet = wallet(now);
        wallet.add(10, 0, 0);

        WalletView afterPostcards = service.exchange(1L, new ExchangeRequest(ExchangeTarget.POSTCARD, 3));
        assertThat(afterPostcards.bookmarkBalance()).isEqualTo(7);
        assertThat(afterPostcards.postcardBalance()).isEqualTo(3);

        WalletView afterStamps = service.exchange(1L, new ExchangeRequest(ExchangeTarget.STAMP, 3));
        assertThat(afterStamps.bookmarkBalance()).isEqualTo(1);
        assertThat(afterStamps.stampBalance()).isEqualTo(3);

        assertThatThrownBy(() -> service.exchange(1L, new ExchangeRequest(ExchangeTarget.STAMP, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BOOKMARK);
    }

    @Test
    @DisplayName("우표 지불 — 없으면 INSUFFICIENT_STAMP, 있으면 차감하고 원장에 남긴다")
    void stampPayment() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Wallet wallet = wallet(now);

        assertThatThrownBy(() -> service.payStamp(1L, service.prepared(1L),
                WalletTransactionKind.REPLY_STAMP, 5L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_STAMP);

        wallet.add(0, 0, 1);
        service.payStamp(1L, service.prepared(1L), WalletTransactionKind.REPLY_STAMP, 5L);
        assertThat(wallet.getStampBalance()).isZero();
    }

    @Test
    @DisplayName("지갑이 없으면 만든다 — 처음 여는 사용자")
    void createsWalletLazily() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(walletRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet wallet = service.prepared(7L);
        assertThat(wallet.getUserId()).isEqualTo(7L);
        assertThat(wallet.freePostcardsLeft(5)).isEqualTo(5);
        verify(walletRepository).save(any(Wallet.class));
    }
}
