package app.bookey.domain.user;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String handle;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "notify_tone", nullable = false, length = 20)
    private NotifyTone notifyTone;

    @Column(name = "quiet_hours_start", nullable = false)
    private short quietHoursStart;

    @Column(name = "quiet_hours_end", nullable = false)
    private short quietHoursEnd;

    @Column(name = "daily_notify_cap", nullable = false)
    private short dailyNotifyCap;

    @Column(name = "club_notify_cap", nullable = false)
    private short clubNotifyCap;

    @Column(name = "allow_nudge", nullable = false)
    private boolean allowNudge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Builder
    private User(String handle, String email, String nickname, String avatarUrl, String timezone) {
        this.handle = handle;
        this.email = email;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.timezone = timezone == null ? "Asia/Seoul" : timezone;
        this.notifyTone = NotifyTone.GENTLE;
        this.quietHoursStart = 22;
        this.quietHoursEnd = 8;
        this.dailyNotifyCap = 2;
        this.clubNotifyCap = 3;
        this.allowNudge = true;
        this.status = UserStatus.ACTIVE;
    }

    public void updateProfile(String nickname, String avatarUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void updateNotificationSettings(NotifyTone tone, Short quietStart, Short quietEnd,
                                           Short dailyCap, Short clubCap, Boolean allowNudge) {
        if (tone != null) {
            this.notifyTone = tone;
        }
        if (quietStart != null) {
            this.quietHoursStart = quietStart;
        }
        if (quietEnd != null) {
            this.quietHoursEnd = quietEnd;
        }
        if (dailyCap != null) {
            this.dailyNotifyCap = dailyCap;
        }
        if (clubCap != null) {
            this.clubNotifyCap = clubCap;
        }
        if (allowNudge != null) {
            this.allowNudge = allowNudge;
        }
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** 조용 시간 여부 (§F5 설계 원칙 3). 자정을 넘는 구간도 처리한다. */
    public boolean isQuietHour(int hour) {
        if (quietHoursStart == quietHoursEnd) {
            return false;
        }
        if (quietHoursStart < quietHoursEnd) {
            return hour >= quietHoursStart && hour < quietHoursEnd;
        }
        return hour >= quietHoursStart || hour < quietHoursEnd;
    }
}
