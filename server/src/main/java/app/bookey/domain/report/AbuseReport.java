package app.bookey.domain.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 사용자 신고 (§8.3). 접수 시 moderation_queue 로 승격된다. */
@Getter
@Entity
@Table(name = "abuse_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(nullable = false, length = 15)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public AbuseReport(String targetType, Long targetId, Long reporterId, String reason, String detail) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
        this.status = "PENDING";
    }

    public void resolve() {
        this.status = "RESOLVED";
    }

    public void reject() {
        this.status = "REJECTED";
    }
}
