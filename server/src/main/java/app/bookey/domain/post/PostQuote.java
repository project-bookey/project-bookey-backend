package app.bookey.domain.post;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 독후감에 인용한 밑줄(오려둔 문장) 연결. 같은 밑줄은 독후감당 한 번만. */
@Getter
@Entity
@Table(name = "post_quotes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostQuote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Builder
    private PostQuote(Long postId, Long quoteId, short sortOrder) {
        this.postId = postId;
        this.quoteId = quoteId;
        this.sortOrder = sortOrder;
    }
}
