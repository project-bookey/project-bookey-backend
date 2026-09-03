package app.bookey.domain.quote;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밑줄에 덧붙인 말(댓글) — 1단계 답글까지, 본인만 삭제한다. */
@Getter
@Entity
@Table(name = "quote_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 답글이면 부모 댓글 id, 최상위 댓글이면 null. 부모를 지우면 DB CASCADE 로 함께 사라진다. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 300)
    private String body;

    @Builder
    private QuoteComment(Long quoteId, Long userId, Long parentId, String body) {
        this.quoteId = quoteId;
        this.userId = userId;
        this.parentId = parentId;
        this.body = body;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 경로의 밑줄과 댓글의 밑줄이 같은지 — 다른 밑줄 경로로 남의 댓글을 지우는 것을 막는다. */
    public boolean belongsTo(Long quoteId) {
        return this.quoteId.equals(quoteId);
    }

    /** 답글인지 — 답글에는 다시 답글을 달 수 없다(1단계). */
    public boolean isReply() {
        return parentId != null;
    }
}
