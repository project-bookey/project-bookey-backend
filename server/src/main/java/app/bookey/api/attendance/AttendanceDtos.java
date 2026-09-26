package app.bookey.api.attendance;

import java.time.LocalDate;

public final class AttendanceDtos {
    private AttendanceDtos() {}

    public record AttendanceView(
            boolean checkedInToday,
            int monthlyAttendanceDays,
            int monthlyMaxDays,
            int rewardEveryDays,
            Integer nextRewardDay,
            String nextRewardType,
            int rewardedPostcards,
            int rewardedStamps,
            int postcardBalance,
            int stampBalance,
            LocalDate attendanceDate
    ) {}
}
