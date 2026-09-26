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
    private final AttendanceService service = new AttendanceService(repository, walletService, CLOCK);

    @Test
    void firstCheckInStartsMonthlyBoardWithoutReward() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(repository.countByUserIdAndAttendanceDateBetween(eq(USER_ID), any(), any())).thenReturn(0L);

        var result = service.checkIn(USER_ID);

        assertThat(result.checkedInToday()).isTrue();
        assertThat(result.monthlyAttendanceDays()).isEqualTo(1);
        assertThat(result.rewardedStamps()).isZero();
        assertThat(result.nextRewardDay()).isEqualTo(7);
        verify(repository).save(any(DailyAttendance.class));
        verify(walletService, never()).grantAttendanceStamps(anyLong(), any(), anyInt());
    }

    @Test
    void duplicateCheckInDoesNotGrantAgain() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        wallet.add(0, 0, 2);
        DailyAttendance existing = new DailyAttendance(USER_ID, TODAY, 4, 0, 0);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));
        when(repository.countByUserIdAndAttendanceDateBetween(eq(USER_ID), any(), any())).thenReturn(4L);

        var result = service.checkIn(USER_ID);

        assertThat(result.monthlyAttendanceDays()).isEqualTo(4);
        assertThat(result.rewardedStamps()).isZero();
        assertThat(result.stampBalance()).isEqualTo(2);
        verify(repository, never()).save(any());
        verify(walletService, never()).grantAttendanceStamps(anyLong(), any(), anyInt());
    }

    @Test
    void seventhCheckInGrantsOneStamp() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(repository.countByUserIdAndAttendanceDateBetween(eq(USER_ID), any(), any())).thenReturn(6L);
        doAnswer(invocation -> {
            Wallet target = invocation.getArgument(1);
            target.add(0, 0, invocation.getArgument(2));
            return null;
        }).when(walletService).grantAttendanceStamps(eq(USER_ID), same(wallet), eq(1));

        var result = service.checkIn(USER_ID);

        assertThat(result.monthlyAttendanceDays()).isEqualTo(7);
        assertThat(result.rewardedStamps()).isEqualTo(1);
        assertThat(result.stampBalance()).isEqualTo(1);
        assertThat(result.nextRewardDay()).isEqualTo(14);
    }

    @Test
    void monthlyBoardStopsAtTwentyEightDays() {
        Wallet wallet = new Wallet(USER_ID, TODAY);
        when(walletService.prepared(USER_ID)).thenReturn(wallet);
        when(repository.findByUserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(repository.countByUserIdAndAttendanceDateBetween(eq(USER_ID), any(), any())).thenReturn(28L);

        var result = service.checkIn(USER_ID);

        assertThat(result.checkedInToday()).isFalse();
        assertThat(result.monthlyAttendanceDays()).isEqualTo(28);
        assertThat(result.nextRewardDay()).isNull();
        verify(repository, never()).save(any());
    }
}
