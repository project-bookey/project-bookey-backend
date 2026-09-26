package app.bookey.domain.attendance;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "daily_attendances", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_attendance_user_date", columnNames = {"user_id", "attendance_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAttendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "streak_days", nullable = false)
    private int streakDays;

    @Column(name = "reward_bookmarks", nullable = false)
    private int rewardBookmarks;

    @Column(name = "reward_stamps", nullable = false)
    private int rewardStamps;

    @Column(name = "reward_postcards", nullable = false)
    private int rewardPostcards;

    public DailyAttendance(Long userId, LocalDate attendanceDate, int streakDays,
                           int rewardBookmarks, int rewardPostcards, int rewardStamps) {
        this.userId = userId;
        this.attendanceDate = attendanceDate;
        this.streakDays = streakDays;
        this.rewardBookmarks = rewardBookmarks;
        this.rewardPostcards = rewardPostcards;
        this.rewardStamps = rewardStamps;
    }
}
