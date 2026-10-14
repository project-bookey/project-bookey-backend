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
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private NovelStatus status;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, length = 30) private String genre;
    @Column(name = "cover_id") private Long coverId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private NovelMode mode;
    /** 열매로 퍼뜨리는 작품(OPEN)은 작가 수 제한이 없어 비어 있다. */
    @Column(name = "member_limit") private Integer memberLimit;
    /** OPEN 작품이 회차 하나를 몇 명에게 배달할지. */
    @Column(name = "delivery_count") private Integer deliveryCount;
    @Column(name = "base_work_id") private Long baseWorkId;
    @Column(name = "chapter_limit", nullable = false) private int chapterLimit;
    @Column(name = "turn_hours", nullable = false) private int turnHours;
    @Column(name = "chapter_count", nullable = false) private int chapterCount;
    @Column(name = "turn_number", nullable = false) private long turnNumber;
    @Column(name = "current_writer_id") private Long currentWriterId;
    @Column(name = "turn_due_at") private Instant turnDueAt;

    public Novel(Long ownerId, NovelKind kind, String title, String description, String genre,
                 int memberLimit, int chapterLimit, int turnHours, boolean isPublic) {
        this.ownerId = ownerId;
        this.isPublic = isPublic;
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
        this.mode = NovelMode.CIRCLE;
    }

    /** 열매로 퍼뜨리는 작품 — 바로 연재가 시작되고 만든 사람이 1회차를 쓴다. 그 뒤 회차는 배달로만 생긴다. */
    public static Novel open(Long ownerId, String title, String description, String genre, int chapterLimit,
                             int turnHours, int deliveryCount, Long baseWorkId) {
        Novel n = new Novel(ownerId, NovelKind.RELAY, title, description, genre, 1, chapterLimit, turnHours, true);
        n.mode = NovelMode.OPEN;
        n.memberLimit = null;
        n.deliveryCount = deliveryCount;
        n.baseWorkId = baseWorkId;
        n.status = NovelStatus.ONGOING;
        n.currentWriterId = ownerId;
        return n;
    }
    public void useBaseWork(Long baseWorkId) { this.baseWorkId = baseWorkId; }
    public boolean isOpen() { return mode == NovelMode.OPEN; }
    /** 이 깊이의 회차에서 가지를 더 뻗을 수 있는가 — 목표 회차에 닿은 가지는 끝난다. */
    public boolean canGrowFrom(int chapterNumber) {
        return status == NovelStatus.ONGOING && chapterNumber < chapterLimit;
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
        if (mode == NovelMode.OPEN) {
            // 1회차가 나오면 차례는 비고, 이어 쓰기는 배달받은 사람들의 가지로 이어진다.
            currentWriterId = null;
            turnDueAt = null;
            if (chapterLimit <= 1) complete();
            return;
        }
        if (chapterCount >= chapterLimit) complete();
        else if (kind == NovelKind.RELAY) advance(writers, 1, now);
    }
    /** 배달받은 사람이 가지를 하나 뻗었다. 작품 전체의 완결은 만든 사람이 정한다. */
    public void addBranch() { chapterCount++; }
    /** 지연된 배치도 경과한 차례 수를 한 번에 계산해 무한 반복하지 않는다. */
    public void expire(List<Long> writers, Instant now) {
        if (status != NovelStatus.ONGOING || kind != NovelKind.RELAY || mode == NovelMode.OPEN || turnDueAt == null
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
