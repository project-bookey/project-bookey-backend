package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Entity
@Table(name = "novels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Novel extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private NovelKind kind;
    @Column(name = "is_public", nullable = false) private boolean isPublic;
    @Column(name = "invite_code", nullable = false, unique = true, length = 32) private String inviteCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private NovelStatus status;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, length = 30) private String genre;
    @Column(name = "cover_id") private Long coverId;
    @Column(name = "member_limit", nullable = false) private int memberLimit;
    @Column(name = "chapter_limit", nullable = false) private int chapterLimit;
    @Column(name = "turn_hours", nullable = false) private int turnHours;
    @Column(name = "chapter_count", nullable = false) private int chapterCount;
    @Column(name = "turn_number", nullable = false) private long turnNumber;
    @Column(name = "current_writer_id") private Long currentWriterId;
    @Column(name = "turn_due_at") private Instant turnDueAt;

    public Novel(Long ownerId, NovelKind kind, String title, String description, String genre,
                 int memberLimit, int chapterLimit, int turnHours, boolean isPublic, String inviteCode) {
        this.ownerId = ownerId;
        this.isPublic = isPublic;
        this.inviteCode = inviteCode;
        this.kind = kind;
        this.title = title.strip();
        this.description = description == null ? "" : description.strip();
        this.genre = genre.strip();
        this.memberLimit = kind == NovelKind.SOLO ? 1 : memberLimit;
        this.chapterLimit = chapterLimit;
        this.turnHours = turnHours;
        this.status = kind == NovelKind.SOLO ? NovelStatus.ONGOING : NovelStatus.RECRUITING;
        this.currentWriterId = kind == NovelKind.SOLO ? ownerId : null;
        this.turnNumber = 1;
    }

    public void changeCover(Long coverId) { this.coverId = coverId; }
    public boolean isOwner(Long userId) { return ownerId.equals(userId); }
    public boolean canWrite(Long userId) {
        return status == NovelStatus.ONGOING && userId.equals(currentWriterId);
    }
    public void start(Instant now) {
        status = NovelStatus.ONGOING;
        currentWriterId = ownerId;
        turnDueAt = now.plus(Duration.ofHours(turnHours));
    }
    public void complete() {
        status = NovelStatus.COMPLETED;
        currentWriterId = null;
        turnDueAt = null;
    }
    /** 한 번 저장된 회차는 다시 쓰지 않는다. 다음 차례는 회차와 같은 트랜잭션에서 연다. */
    public void publish(List<Long> writers, Instant now) {
        chapterCount++;
        turnNumber++;
        if (chapterCount >= chapterLimit) complete();
        else if (kind == NovelKind.RELAY) advance(writers, 1, now);
    }
    /** 지연된 배치도 경과한 차례 수를 한 번에 계산해 무한 반복하지 않는다. */
    public void expire(List<Long> writers, Instant now) {
        if (status != NovelStatus.ONGOING || kind != NovelKind.RELAY || turnDueAt == null
                || now.isBefore(turnDueAt)) return;
        long seconds = Duration.ofHours(turnHours).toSeconds();
        long skipped = Duration.between(turnDueAt, now).getSeconds() / seconds + 1;
        Instant nextStart = turnDueAt.plusSeconds((skipped - 1) * seconds);
        turnNumber += skipped;
        advance(writers, skipped, nextStart);
    }
    public void skip(List<Long> writers, Instant now) {
        turnNumber++;
        advance(writers, 1, now);
    }
    private void advance(List<Long> writers, long steps, Instant start) {
        if (writers.isEmpty()) { complete(); return; }
        int index = writers.indexOf(currentWriterId);
        currentWriterId = writers.get((int) Math.floorMod(index + steps, writers.size()));
        turnDueAt = start.plus(Duration.ofHours(turnHours));
    }
}
