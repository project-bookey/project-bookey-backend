package app.bookey.api.attendance;

import app.bookey.api.attendance.AttendanceDtos.AttendanceView;
import app.bookey.api.social.WalletService;
import app.bookey.domain.attendance.DailyAttendance;
import app.bookey.domain.attendance.DailyAttendanceRepository;
import app.bookey.domain.wallet.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AttendanceService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MONTHLY_MAX_DAYS = 28;
    private static final int REWARD_EVERY_DAYS = 7;

    private final DailyAttendanceRepository attendanceRepository;
    private final WalletService walletService;
    private final Clock clock;

    public AttendanceService(DailyAttendanceRepository attendanceRepository,
                             WalletService walletService,
                             Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.walletService = walletService;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), KST);
    }

    @Transactional(readOnly = true)
    public AttendanceView status(Long userId) {
        LocalDate today = today();
        DailyAttendance todayAttendance = attendanceRepository
                .findByUserIdAndAttendanceDate(userId, today).orElse(null);
        int monthlyDays = monthlyDays(userId, today);
        Wallet balances = walletService.find(userId);
        return view(todayAttendance != null, monthlyDays, 0, 0,
                balances == null ? 0 : balances.getPostcardBalance(),
                balances == null ? 0 : balances.getStampBalance(),
                todayAttendance == null ? null : today);
    }

    @Transactional
    public AttendanceView checkIn(Long userId) {
        LocalDate today = today();

        // 사용자 지갑 행을 잠가 같은 사용자의 동시 출석 요청을 직렬화한다.
        Wallet wallet = walletService.prepared(userId);
        int monthlyDays = monthlyDays(userId, today);
        DailyAttendance existing = attendanceRepository.findByUserIdAndAttendanceDate(userId, today).orElse(null);
        if (existing != null) {
            return view(true, monthlyDays, 0, 0,
                    wallet.getPostcardBalance(), wallet.getStampBalance(), today);
        }
        if (monthlyDays >= MONTHLY_MAX_DAYS) {
            return view(false, MONTHLY_MAX_DAYS, 0, 0,
                    wallet.getPostcardBalance(), wallet.getStampBalance(), null);
        }

        int checkedDays = monthlyDays + 1;
        boolean milestone = checkedDays % REWARD_EVERY_DAYS == 0;
        int rewardedPostcards = milestone && (checkedDays == 7 || checkedDays == 21) ? 1 : 0;
        int rewardedStamps = milestone && (checkedDays == 14 || checkedDays == 28) ? 1 : 0;
        attendanceRepository.save(new DailyAttendance(
                userId, today, checkedDays, 0, rewardedPostcards, rewardedStamps));
        if (rewardedPostcards > 0 || rewardedStamps > 0) {
            walletService.grantAttendanceReward(userId, wallet, rewardedPostcards, rewardedStamps);
        }
        return view(true, checkedDays, rewardedPostcards, rewardedStamps,
                wallet.getPostcardBalance(), wallet.getStampBalance(), today);
    }

    private int monthlyDays(Long userId, LocalDate today) {
        LocalDate first = today.withDayOfMonth(1);
        LocalDate last = today.withDayOfMonth(today.lengthOfMonth());
        return (int) Math.min(MONTHLY_MAX_DAYS,
                attendanceRepository.countByUserIdAndAttendanceDateBetween(userId, first, last));
    }

    private AttendanceView view(boolean checkedToday, int monthlyDays,
                                int rewardedPostcards, int rewardedStamps,
                                int postcardBalance, int stampBalance, LocalDate attendanceDate) {
        Integer nextRewardDay = monthlyDays >= MONTHLY_MAX_DAYS
                ? null
                : Math.min(MONTHLY_MAX_DAYS, ((monthlyDays / REWARD_EVERY_DAYS) + 1) * REWARD_EVERY_DAYS);
        String nextRewardType = nextRewardDay == null
                ? null
                : (nextRewardDay == 7 || nextRewardDay == 21 ? "POSTCARD" : "STAMP");
        return new AttendanceView(checkedToday, monthlyDays, MONTHLY_MAX_DAYS, REWARD_EVERY_DAYS,
                nextRewardDay, nextRewardType, rewardedPostcards, rewardedStamps,
                postcardBalance, stampBalance, attendanceDate);
    }
}
