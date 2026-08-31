package app.bookey.domain.club;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모임 토론 글 (§12.3). */
@Getter
@Entity
@Table(name = "club_posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "club_book_id")
    private Long clubBookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClubPostType type = ClubPostType.DISCUSSION;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** "이 이야기는 몇 쪽 기준인가" — 스포일러 가드의 기준값. */
    @Column(name = "anchor_page")
    private Integer anchorPage;

    @Enumerated(EnumType.STRING)
    @Column(name = "spoiler_level", nullable = false, length = 6)
    private SpoilerLevel spoilerLevel = SpoilerLevel.PAGE;

    @Column(name = "linked_post_id")
    private Long linkedPostId;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(nullable = false, length = 10)
    private String status = "VISIBLE";

    @Builder
    private ClubPost(Long clubId, Long clubBookId, Long userId, Long parentId, ClubPostType type,
                     String body, Integer anchorPage, SpoilerLevel spoilerLevel, Long linkedPostId) {
        this.clubId = clubId;
        this.clubBookId = clubBookId;
        this.userId = userId;
        this.parentId = parentId;
        this.type = type == null ? ClubPostType.DISCUSSION : type;
        this.body = body;
        this.anchorPage = anchorPage;
        this.spoilerLevel = resolveSpoilerLevel(spoilerLevel, anchorPage);
        this.linkedPostId = linkedPostId;
        this.status = "VISIBLE";
    }

    private static SpoilerLevel resolveSpoilerLevel(SpoilerLevel requested, Integer anchorPage) {
        if (requested != null) {
            return requested;
        }
        return anchorPage != null ? SpoilerLevel.PAGE : SpoilerLevel.NONE;
    }

    /**
     * 이 글이 뷰어에게 가려져야 하는가 (§12.3 스포일러 가드).
     *
     * @param viewerPage     뷰어의 현재 진도
     * @param viewerFinished 뷰어의 완독 여부
     * @param isAuthor       뷰어가 작성자인지
     */
    public boolean isMaskedFor(int viewerPage, boolean viewerFinished, boolean isAuthor) {
        if (isAuthor || viewerFinished) {
            return false;
        }
        return switch (spoilerLevel) {
            case NONE -> false;
            case BOOK -> true;
            case PAGE -> anchorPage != null && anchorPage > viewerPage;
        };
    }

    public void edit(String body, Integer anchorPage, SpoilerLevel spoilerLevel) {
        if (body != null && !body.isBlank()) {
            this.body = body;
        }
        if (anchorPage != null) {
            this.anchorPage = anchorPage;
        }
        if (spoilerLevel != null) {
            this.spoilerLevel = spoilerLevel;
        }
    }

    public void pin(boolean pinned) {
        this.pinned = pinned;
    }

    public void increaseComment() {
        this.commentCount++;
    }

    public void decreaseComment() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public void changeReactionCount(int delta) {
        this.reactionCount = Math.max(0, this.reactionCount + delta);
    }

    public void addReport() {
        this.reportCount++;
    }

    public void hide() {
        this.status = "HIDDEN";
    }

    public void restore() {
        this.status = "VISIBLE";
    }

    public void softDelete() {
        this.status = "DELETED";
        this.body = "";
    }

    public boolean isVisible() {
        return "VISIBLE".equals(status);
    }

    public boolean isComment() {
        return parentId != null;
    }

    public boolean isAuthor(Long userId) {
        return this.userId.equals(userId);
    }
}
