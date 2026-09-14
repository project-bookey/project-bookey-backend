package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.ClubSeatResult;
import app.bookey.api.club.dto.ClubDtos.ExpandSeatsRequest;
import app.bookey.api.social.SubscriptionService;
import app.bookey.api.social.WalletService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletRepository;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 모임 자리 늘리기 — 호스트만, 자리당 책갈피 4개, 최대 6명, 원장 기록. */
class ClubSeatServiceTest {

    private static final long HOST_ID = 1L;
    private static final long CLUB_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-09-14T03:00:00Z");
    private static final BookeyProperties.Club CLUB_POLICY =
            new BookeyProperties.Club(3, 6, 3, 4, Duration.ofHours(24), 3, 10);
    private static final BookeyProperties.Social SOCIAL =
            new BookeyProperties.Social(5, 16, 1, 2, 50, 30, 17900);

    private final ClubRepository clubRepository = mock(ClubRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final Clock clock = mock(Clock.class);
    private final BookeyProperties properties =
            new BookeyProperties(null, null, null, null, CLUB_POLICY, null, SOCIAL, null, null, null);
    private final WalletService walletService = new WalletService(
            walletRepository, transactionRepository, mock(SubscriptionService.class), properties, clock);
    private final ClubSeatService service =
            new ClubSeatService(clubRepository, walletService, rateLimiter, properties);

    private Club club(int memberLimit) {
        Club club = Club.builder()
                .ownerId(HOST_ID)
                .name("월요일의 데미안")
                .joinCode("ABC234")
                .memberLimit((short) memberLimit)
                .startsAt(LocalDate.of(2026, 9, 1))
                .endsAt(LocalDate.of(2026, 9, 30))
                .allowNudge(true)
                .build();
        when(clubRepository.findByIdForUpdate(CLUB_ID)).thenReturn(Optional.of(club));
        return club;
    }

    private Wallet wallet(int bookmarks) {
        when(clock.instant()).thenReturn(NOW);
        Wallet wallet = new Wallet(HOST_ID, LocalDate.ofInstant(NOW, ZoneId.of("Asia/Seoul")));
        wallet.add(bookmarks, 0, 0);
        when(walletRepository.findByUserIdForUpdate(HOST_ID)).thenReturn(Optional.of(wallet));
        return wallet;
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("3명 → 6명은 책갈피 12개를 쓰고, 원장에 모임 참조를 남긴다")
    void expandThreeSeats() {
        Club club = club(3);
        wallet(15);

        ClubSeatResult result = service.expand(HOST_ID, CLUB_ID, new ExpandSeatsRequest(6));

        assertThat(result.memberLimit()).isEqualTo(6);
        assertThat(result.bookmarkBalance()).isEqualTo(3);
        assertThat(club.getMemberLimit()).isEqualTo((short) 6);

        ArgumentCaptor<WalletTransaction> tx = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getKind()).isEqualTo(WalletTransactionKind.CLUB_SEAT);
        assertThat(tx.getValue().getBookmarkDelta()).isEqualTo(-12);
        assertThat(tx.getValue().getRefType()).isEqualTo("CLUB");
        assertThat(tx.getValue().getRefId()).isEqualTo(CLUB_ID);
    }

    @Test
    @DisplayName("이미 늘린 모임은 차이만큼만 낸다 — 4명 → 5명은 책갈피 4개")
    void expandChargesOnlyDifference() {
        club(4);
        wallet(4);

        ClubSeatResult result = service.expand(HOST_ID, CLUB_ID, new ExpandSeatsRequest(5));

        assertThat(result.memberLimit()).isEqualTo(5);
        assertThat(result.bookmarkBalance()).isZero();
    }

    @Test
    @DisplayName("호스트가 아니면 자리를 늘릴 수 없고 책갈피도 건드리지 않는다")
    void rejectsNonHost() {
        club(3);

        assertThatThrownBy(() -> service.expand(2L, CLUB_ID, new ExpandSeatsRequest(4)))
                .isInstanceOf(ApiException.class)
                .extracting(ClubSeatServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOT_HOST);
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("책갈피가 모자라면 INSUFFICIENT_BOOKMARK 이고 원장에 남지 않는다")
    void rejectsInsufficientBookmarks() {
        club(3);
        Wallet wallet = wallet(5);

        assertThatThrownBy(() -> service.expand(HOST_ID, CLUB_ID, new ExpandSeatsRequest(6)))
                .extracting(ClubSeatServiceTest::codeOf)
                .isEqualTo(ErrorCode.INSUFFICIENT_BOOKMARK);
        assertThat(wallet.getBookmarkBalance()).isEqualTo(5);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("최대 정원을 넘기면 결제 전에 거절한다")
    void rejectsOverMaxBeforePaying() {
        club(3);

        assertThatThrownBy(() -> service.expand(HOST_ID, CLUB_ID, new ExpandSeatsRequest(7)))
                .extracting(ClubSeatServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("없는 모임은 CLUB_NOT_FOUND")
    void rejectsMissingClub() {
        when(clubRepository.findByIdForUpdate(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.expand(HOST_ID, CLUB_ID, new ExpandSeatsRequest(4)))
                .extracting(ClubSeatServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOT_FOUND);
    }
}
