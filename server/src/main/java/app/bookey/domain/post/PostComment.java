package app.bookey.domain.post;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 독후감에 달린 댓글 — 루트와 답글 2단계까지만, 본인만 삭제한다. */
@Getter
@Entity
@Table(name = "post_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 300)
    private String body;

    @Builder
    private PostComment(Long postId, Long userId, Long parentId, String body) {
        this.postId = postId;
        this.userId = userId;
        this.parentId = parentId;
        this.body = body;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 경로의 독후감과 댓글의 독후감이 같은지 — 다른 독후감 경로로 남의 댓글을 지우는 것을 막는다. */
    public boolean belongsTo(Long postId) {
        return this.postId.equals(postId);
    }

    public boolean isReply() {
        return parentId != null;
    }

    /** 답글의 답글은 막는다 — 루트 댓글에만 답글을 달 수 있다. */
    public static boolean canReplyTo(PostComment parent) {
        return !parent.isReply();
    }
}
