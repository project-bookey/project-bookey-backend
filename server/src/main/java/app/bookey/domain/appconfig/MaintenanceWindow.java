package app.bookey.domain.appconfig;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 점검 일정. 취소하면 행은 남기고 cancelledAt 만 찍는다(언제 무엇을 예고했는지 남기기 위해). */
@Getter
@Entity
@Table(name = "maintenance_windows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceWindow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public MaintenanceWindow(String title, String message, Instant startsAt, Instant endsAt, Long createdBy) {
        this.title = title;
        this.message = message;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
    }

    public void update(String title, String message, Instant startsAt, Instant endsAt) {
        this.title = title;
        this.message = message;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void cancel(Instant now) {
        this.cancelledAt = now;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** 지금 점검 중인지. */
    public boolean isActiveAt(Instant now) {
        return !isCancelled() && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    /** 아직 끝나지 않은(예정 또는 진행 중) 점검인지. */
    public boolean isUpcomingOrActiveAt(Instant now) {
        return !isCancelled() && now.isBefore(endsAt);
    }
}
