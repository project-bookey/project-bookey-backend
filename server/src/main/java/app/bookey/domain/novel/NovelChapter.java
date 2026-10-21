package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Duration;
import java.time.Instant;

@Getter @Entity @Table(name = "novel_chapters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelChapter extends BaseTimeEntity {
    /** 판정일부터 이만큼 지나도 조회가 모자라면 끝 가지를 수거한다. */
    public static final Duration JUDGE_AFTER = Duration.ofDays(30);
    public static final int MIN_VIEWS = 50;
    /** 수거함에서 살리거나 버릴 수 있는 시간. 지나면 지운다. */
    public static final Duration BIN_GRACE = Duration.ofDays(7);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Column(name = "author_id") private Long authorId;
    /** 가지의 깊이. 뿌리 회차가 1이고, 가지를 뻗을 때마다 부모보다 1 크다. */
    @Column(name = "chapter_number", nullable = false) private int chapterNumber;
    /** 이 회차가 뻗어 나온 회차. 뿌리 회차만 비어 있다. */
    @Column(name = "parent_chapter_id") private Long parentChapterId;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private NovelChapterState state;
    /** 글쓴이를 뺀, 이 회차를 연 사람 수. 한 사람은 한 번만 센다. */
    @Column(name = "view_count", nullable = false) private int viewCount;
    /** 수거 판정을 세기 시작한 때 — 처음엔 쓴 때, 살리면 살린 때. */
    @Column(name = "judged_from", nullable = false) private Instant judgedFrom;
    @Column(name = "pruned_at") private Instant prunedAt;
    @Column(name = "purge_at") private Instant purgeAt;
    /** 첫 작가의 줄기 — 자기 회차로만 위로 이어지고, 가지 자리에 들지 않으며 수거되지 않는다. */
    @Column(nullable = false) private boolean trunk;

    public NovelChapter(Long novelId, Long authorId, Long parentChapterId, int chapterNumber, String title, String body) {
        this.novelId = novelId; this.authorId = authorId; this.parentChapterId = parentChapterId;
        this.chapterNumber = chapterNumber; this.title = title.strip(); this.body = body.strip();
        this.state = NovelChapterState.ACTIVE;
        this.judgedFrom = Instant.now();
    }

    /** 첫 작가의 줄기 회차를 만든다. 뿌리 회차도 줄기다. */
    public static NovelChapter trunk(Long novelId, Long authorId, NovelChapter tip, String title, String body) {
        NovelChapter c = new NovelChapter(novelId, authorId, tip == null ? null : tip.getId(),
                tip == null ? 1 : tip.getChapterNumber() + 1, title, body);
        c.trunk = true;
        return c;
    }

    public boolean isActive() { return state == NovelChapterState.ACTIVE; }
    public boolean isPruned() { return state == NovelChapterState.PRUNED; }
    public boolean isRoot() { return parentChapterId == null; }
    public boolean isWrittenBy(Long userId) { return authorId != null && authorId.equals(userId); }
    /** 판정 기간이 지났는데 조회가 모자라다. 끝 가지인지는 부르는 쪽이 본다. 줄기는 떨어지지 않는다. */
    public boolean isStale(Instant now) {
        return isActive() && !isRoot() && !trunk && viewCount < MIN_VIEWS && !now.isBefore(judgedFrom.plus(JUDGE_AFTER));
    }
    public void prune(Instant now) {
        state = NovelChapterState.PRUNED;
        prunedAt = now;
        purgeAt = now.plus(BIN_GRACE);
    }
    public boolean isPurgeDue(Instant now) { return isPruned() && !now.isBefore(purgeAt); }
    /** 살린 가지는 살린 날부터 한 달 뒤에 다시 판정한다. */
    public void restore(Instant now) {
        state = NovelChapterState.ACTIVE;
        judgedFrom = now;
        prunedAt = null;
        purgeAt = null;
    }
    public void remove() { state = NovelChapterState.REMOVED; }
}
