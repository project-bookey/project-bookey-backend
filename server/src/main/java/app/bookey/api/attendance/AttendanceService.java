package app.bookey.api.attendance;

import app.bookey.api.attendance.AttendanceDtos.AttendanceView;
import app.bookey.api.social.WalletService;
import app.bookey.domain.attendance.DailyAttendance;
import app.bookey.domain.attendance.DailyAttendanceRepository;
import app.bookey.domain.wallet.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AttendanceService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyAttendanceRepository attendanceRepository;
    private final WalletService walletService;
    private final Clock clock;

    private final int dailyRewardBookmarks;

    public AttendanceService(DailyAttendanceRepository attendanceRepository,
                             WalletService walletService,
                             Clock clock,
                             @Value("${bookey.attendance.daily-reward-bookmarks:1}") int dailyRewardBookmarks) {
        this.attendanceRepository = attendanceRepository;
        this.walletService = walletService;
        this.clock = clock;
        this.dailyRewardBookmarks = dailyRewardBookmarks;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), KST);
    }

    @Transactional(readOnly = true)
    public AttendanceView status(Long userId) {
        LocalDate today = today();
        DailyAttendance latest = attendanceRepository.findTopByUserIdOrderByAttendanceDateDesc(userId).orElse(null);
        boolean checked = latest != null && latest.getAttendanceDate().equals(today);
        int streak = checked ? latest.getStreakDays() : 0;
        int balance = walletService.balance(userId);
        return new AttendanceView(checked, streak, dailyRewardBookmarks, 0, balance,
                checked ? latest.getAttendanceDate() : null);
    }

    @Transactional
    public AttendanceView checkIn(Long userId) {
        LocalDate today = today();

        // 사용자 지갑 행을 잠가 같은 사용자의 동시 출석 요청을 직렬화한다.
        Wallet wallet = walletService.prepared(userId);
        DailyAttendance existing = attendanceRepository.findByUserIdAndAttendanceDate(userId, today).orElse(null);
        if (existing != null) {
            return new AttendanceView(true, existing.getStreakDays(), dailyRewardBookmarks, 0,
                    wallet.getBookmarkBalance(), today);
        }

        DailyAttendance latest = attendanceRepository.findTopByUserIdOrderByAttendanceDateDesc(userId).orElse(null);
        int streak = latest != null && latest.getAttendanceDate().equals(today.minusDays(1))
                ? latest.getStreakDays() + 1
                : 1;
        attendanceRepository.save(new DailyAttendance(userId, today, streak, dailyRewardBookmarks));
        walletService.grantAttendanceBookmarks(userId, wallet, dailyRewardBookmarks);
        return new AttendanceView(true, streak, dailyRewardBookmarks, dailyRewardBookmarks,
                wallet.getBookmarkBalance(), today);
    }
}
