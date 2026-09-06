package app.bookey.domain.wallet;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재화 원장 — 모든 증감은 한 행씩 남는다. 잔액은 이 원장의 캐시일 뿐이다. */
@Getter
@Entity
@Table(name = "wallet_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WalletTransactionKind kind;

    @Column(name = "bookmark_delta", nullable = false)
    private int bookmarkDelta;

    @Column(name = "postcard_delta", nullable = false)
    private int postcardDelta;

    @Column(name = "stamp_delta", nullable = false)
    private int stampDelta;

    @Column(name = "ref_type", length = 20)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Builder
    private WalletTransaction(Long userId, WalletTransactionKind kind,
                              int bookmarkDelta, int postcardDelta, int stampDelta,
                              String refType, Long refId) {
        this.userId = userId;
        this.kind = kind;
        this.bookmarkDelta = bookmarkDelta;
        this.postcardDelta = postcardDelta;
        this.stampDelta = stampDelta;
        this.refType = refType;
        this.refId = refId;
    }
}
