package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** 한 회차를 한 사람에게 열매로 보낸 기록. 받은 사람은 마감 전에 그 회차에서 가지를 뻗어 이어 쓸 수 있다. */
@Getter @Entity @Table(name = "novel_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelDelivery extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Column(name = "chapter_id", nullable = false) private Long chapterId;
    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private NovelDeliveryStatus status;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(name = "written_chapter_id") private Long writtenChapterId;

    public NovelDelivery(Long novelId, Long chapterId, Long recipientId, Instant dueAt) {
        this.novelId = novelId; this.chapterId = chapterId; this.recipientId = recipientId;
        this.dueAt = dueAt; this.status = NovelDeliveryStatus.DELIVERED;
    }
    /** 마감 시각은 포함하지 않는다 — 차례 마감과 같은 규칙. */
    public boolean isHeldBy(Long userId, Instant now) {
        return status == NovelDeliveryStatus.DELIVERED && recipientId.equals(userId) && now.isBefore(dueAt);
    }
    public boolean isOverdue(Instant now) { return status == NovelDeliveryStatus.DELIVERED && !now.isBefore(dueAt); }
    public void written(Long chapterId) { status = NovelDeliveryStatus.WRITTEN; writtenChapterId = chapterId; }
    public void decline() { status = NovelDeliveryStatus.DECLINED; }
    public void expire() { status = NovelDeliveryStatus.EXPIRED; }
}
