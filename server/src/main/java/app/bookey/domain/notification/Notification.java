package app.bookey.domain.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "lag_level")
    private Short lagLevel;

    @Column(name = "reading_record_id")
    private Long readingRecordId;

    @Column(name = "club_id")
    private Long clubId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = Map.of();

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    /** 발송 후 24h 내 독서 세션 발생 — 전환 측정(§F5). */
    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "experiment_variant", length = 40)
    private String experimentVariant;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Builder
    private Notification(Long userId, NotificationType type, Short lagLevel, Long readingRecordId,
                         Long clubId, String title, String body, Map<String, Object> payload,
                         Instant scheduledAt, String experimentVariant) {
        this.userId = userId;
        this.type = type;
        this.lagLevel = lagLevel;
        this.readingRecordId = readingRecordId;
        this.clubId = clubId;
        this.title = title;
        this.body = body;
        this.payload = payload == null ? Map.of() : payload;
        this.scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        this.experimentVariant = experimentVariant;
    }

    public void markSent() {
        this.sentAt = Instant.now();
    }

    public void markOpened() {
        if (this.openedAt == null) {
            this.openedAt = Instant.now();
        }
    }

    public void markConverted() {
        if (this.convertedAt == null) {
            this.convertedAt = Instant.now();
        }
    }
}
