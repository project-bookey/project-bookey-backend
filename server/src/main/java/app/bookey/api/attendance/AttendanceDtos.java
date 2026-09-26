package app.bookey.api.attendance;

import java.time.LocalDate;

public final class AttendanceDtos {
    private AttendanceDtos() {}

    public record AttendanceView(
            boolean checkedInToday,
            int streakDays,
            int dailyRewardBookmarks,
            int rewardedBookmarks,
            int bookmarkBalance,
            LocalDate attendanceDate
    ) {}
}
