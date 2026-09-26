package app.bookey.api.attendance;

import app.bookey.api.social.WalletService;
import app.bookey.domain.attendance.DailyAttendance;
import app.bookey.domain.attendance.DailyAttendanceRepository;
import app.bookey.domain.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-26T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    private final DailyAttendanceRepository repository = mock(DailyAttendanceRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final AttendanceService service = new AttendanceService(repository, walletService, CLOCK, 1);

    @Test
    void firstCheckInGrantsOneBookmark() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(repository.findTopByUserIdOrderByAttendanceDateDesc(USER_ID)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Wallet target = invocation.getArgument(1);
            target.add(invocation.getArgument(2), 0, 0);
            return null;
        }).when(walletService).grantAttendanceBookmarks(eq(USER_ID), same(wallet), eq(1));

        var result = service.checkIn(USER_ID);

        assertThat(result.checkedInToday()).isTrue();
        assertThat(result.streakDays()).isEqualTo(1);
        assertThat(result.rewardedBookmarks()).isEqualTo(1);
        assertThat(result.bookmarkBalance()).isEqualTo(1);
        verify(repository).save(any(DailyAttendance.class));
        verify(walletService).grantAttendanceBookmarks(USER_ID, wallet, 1);
    }

    @Test
    void duplicateCheckInDoesNotGrantAgain() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        wallet.add(3, 0, 0);
        DailyAttendance existing = new DailyAttendance(USER_ID, TODAY, 4, 1);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));

        var result = service.checkIn(USER_ID);

        assertThat(result.streakDays()).isEqualTo(4);
        assertThat(result.rewardedBookmarks()).isZero();
        assertThat(result.bookmarkBalance()).isEqualTo(3);
        verify(repository, never()).save(any());
        verify(walletService, never()).grantAttendanceBookmarks(anyLong(), any(), anyInt());
    }

    @Test
    void yesterdayAttendanceContinuesStreak() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        DailyAttendance yesterday = new DailyAttendance(USER_ID, TODAY.minusDays(1), 6, 1);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(repository.findTopByUserIdOrderByAttendanceDateDesc(USER_ID)).thenReturn(Optional.of(yesterday));

        var result = service.checkIn(USER_ID);

        assertThat(result.streakDays()).isEqualTo(7);
    }
}
