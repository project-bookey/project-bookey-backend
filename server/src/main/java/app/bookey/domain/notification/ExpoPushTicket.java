package app.bookey.domain.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Getter
@Entity
@Table(name = "expo_push_tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpoPushTicket {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true, length = 100)
    private String ticketId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ExpoPushTicket(String ticketId, Long deviceId, Long notificationId, Instant nextCheckAt) {
        this.ticketId = ticketId;
        this.deviceId = deviceId;
        this.notificationId = notificationId;
        this.nextCheckAt = nextCheckAt;
    }

    public void delivered(Instant now) {
        status = "DELIVERED";
        resolvedAt = now;
    }

    public void failed(String code, Instant now) {
        status = "FAILED";
        errorCode = code;
        resolvedAt = now;
    }

    public void retry(Instant now) {
        attemptCount++;
        nextCheckAt = now.plus(Duration.ofMinutes(Math.min(30, 1L << Math.min(attemptCount, (short) 4))));
    }
}
