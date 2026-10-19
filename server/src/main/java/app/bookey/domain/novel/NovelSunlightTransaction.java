package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 햇살 원장 — 모든 증감이 한 행씩 남는다. amount 는 늘면 양수, 쓰면 음수. */
@Getter @Entity @Table(name = "novel_sunlight_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelSunlightTransaction extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private NovelSunlightTransactionKind kind;
    @Column(nullable = false) private int amount;
    @Column(name = "ref_type", length = 30) private String refType;
    @Column(name = "ref_id") private Long refId;

    public NovelSunlightTransaction(Long userId, NovelSunlightTransactionKind kind, int amount, String refType, Long refId) {
        this.userId = userId; this.kind = kind; this.amount = amount; this.refType = refType; this.refId = refId;
    }
}
