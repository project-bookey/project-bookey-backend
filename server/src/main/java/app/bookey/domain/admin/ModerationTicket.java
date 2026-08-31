package app.bookey.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/** 신고 처리 큐. SLA 48h (§F13, §3.3 안티 지표). */
@Getter
@Entity
@Table(name = "moderation_queue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationTicket {

    public static final Duration SLA = Duration.ofHours(48);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ModerationSource sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 1;

    @Column(nullable = false)
    private short priority = 3;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ModerationStatus status = ModerationStatus.PENDING;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModerationResolution resolution;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ModerationTicket(ModerationSource sourceType, Long sourceId, String reason) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.reason = reason;
        this.reportCount = 1;
        this.slaDueAt = Instant.now().plus(SLA);
        this.status = ModerationStatus.PENDING;
    }

    /** 같은 대상에 신고가 누적되면 우선순위를 올린다. 3건이면 임시 비노출 대상(§8.3). */
    public void addReport() {
        this.reportCount++;
        if (reportCount >= 3 && priority > 1) {
            this.priority = 1;
        }
    }

    public void assign(Long adminId) {
        this.assignedAdminId = adminId;
        this.status = ModerationStatus.IN_REVIEW;
    }

    public void resolve(Long adminId, ModerationResolution resolution, String note) {
        this.assignedAdminId = adminId;
        this.resolution = resolution;
        this.resolutionNote = note;
        this.status = ModerationStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    public boolean isOverdue() {
        return status != ModerationStatus.RESOLVED && slaDueAt.isBefore(Instant.now());
    }

    public boolean shouldAutoHide() {
        return reportCount >= 3 && status != ModerationStatus.RESOLVED;
    }
}
