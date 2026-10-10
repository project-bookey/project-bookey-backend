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

    /**
     * 신고 수를 대상의 '처리 전(PENDING)' 신고 건수에 맞춘다. 3건이 넘으면 우선순위를 올린다(임시 비노출 기준, §8.3).
     */
    public void syncReportCount(int pendingReports) {
        this.reportCount = Math.max(1, pendingReports);
        if (reportCount >= 3 && priority > 1) {
            this.priority = 1;
        }
    }

    /**
     * 처리한 대상에 새 신고가 들어오면 다시 연다 — 예전 판정(유지 등)이 새 신고를 묻어 버리지 않게.
     * SLA 를 새로 잡고 담당·판정을 비운다.
     */
    public void reopen(String reason) {
        this.status = ModerationStatus.PENDING;
        this.reason = reason;
        this.resolution = null;
        this.resolutionNote = null;
        this.resolvedAt = null;
        this.assignedAdminId = null;
        this.priority = 3;
        this.slaDueAt = Instant.now().plus(SLA);
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
