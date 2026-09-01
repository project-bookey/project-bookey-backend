package app.bookey.domain.quote;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문장 "나도 그럼" — 서재와 무관한 가벼운 반응. user_id+quote_id 당 1건. */
@Getter
@Entity
@Table(name = "quote_agrees")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteAgree extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Builder
    private QuoteAgree(Long userId, Long quoteId) {
        this.userId = userId;
        this.quoteId = quoteId;
    }
}
