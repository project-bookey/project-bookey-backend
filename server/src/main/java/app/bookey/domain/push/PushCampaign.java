package app.bookey.domain.push;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 관리자 전체 푸시. 잡이 대상자를 회원 id 순으로 조금씩 펼쳐(cursorUserId) 알림 행을 만들고 보낸다.
 * 서버를 여러 대 띄워도 같은 구간을 두 번 펼치지 않게 낙관적 잠금(version)을 건다.
 */
@Getter
@Entity
@Table(name = "push_campaigns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushCampaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PushCampaignKind kind;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PushCampaignStatus status = PushCampaignStatus.SCHEDULED;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cursor_user_id", nullable = false)
    private long cursorUserId;

    @Column(name = "audience_done", nullable = false)
    private boolean audienceDone;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Version
    private long version;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    public PushCampaign(PushCampaignKind kind, String title, String body, String linkUrl, Instant scheduledAt,
                        Long createdBy) {
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
        this.scheduledAt = scheduledAt;
        this.createdBy = createdBy;
        this.status = PushCampaignStatus.SCHEDULED;
    }

    public void edit(PushCampaignKind kind, String title, String body, String linkUrl, Instant scheduledAt) {
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
        this.scheduledAt = scheduledAt;
    }

    public void start(Instant now) {
        this.status = PushCampaignStatus.SENDING;
        this.startedAt = now;
    }

    /** 대상자 한 묶음을 펼쳤다. 마지막 묶음이면 audienceDone. */
    public void advance(long lastUserId, int created, boolean last) {
        this.cursorUserId = lastUserId;
        this.targetCount += created;
        if (last) {
            this.audienceDone = true;
        }
    }

    public void finish(Instant now) {
        this.status = PushCampaignStatus.DONE;
        this.finishedAt = now;
    }

    public void cancel(Long adminId, Instant now) {
        this.status = PushCampaignStatus.CANCELLED;
        this.cancelledBy = adminId;
        this.finishedAt = now;
    }

    public boolean isEditable() {
        return status == PushCampaignStatus.SCHEDULED;
    }

    public boolean isCancellable() {
        return status == PushCampaignStatus.SCHEDULED || status == PushCampaignStatus.SENDING;
    }

    public boolean isMarketing() {
        return kind == PushCampaignKind.MARKETING;
    }
}
