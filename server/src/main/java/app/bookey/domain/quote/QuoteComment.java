package app.bookey.domain.quote;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밑줄에 덧붙인 말(댓글) — 평면 구조, 본인만 삭제한다. */
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

    @Column(nullable = false, length = 300)
    private String body;

    @Builder
    private QuoteComment(Long quoteId, Long userId, String body) {
        this.quoteId = quoteId;
        this.userId = userId;
        this.body = body;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 경로의 밑줄과 댓글의 밑줄이 같은지 — 다른 밑줄 경로로 남의 댓글을 지우는 것을 막는다. */
    public boolean belongsTo(Long quoteId) {
        return this.quoteId.equals(quoteId);
    }
}
