package app.bookey.domain.wallet;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 재화 지갑 (§14.2). 책갈피(기축) · 엽서 · 우표 잔액과 무료 엽서 사용량.
 * 잔액 변화는 반드시 {@link WalletTransaction} 원장과 함께 일어나야 한다 — 서비스가 보장한다.
 */
@Getter
@Entity
@Table(name = "wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "bookmark_balance", nullable = false)
    private int bookmarkBalance;

    @Column(name = "postcard_balance", nullable = false)
    private int postcardBalance;

    @Column(name = "stamp_balance", nullable = false)
    private int stampBalance;

    @Column(name = "free_postcards_used_today", nullable = false)
    private short freePostcardsUsedToday;

    /** KST 기준 날짜 — 날이 바뀌면 무료 사용량이 리셋된다(§14.9 확정: 자정 KST). */
    @Column(name = "free_reset_date", nullable = false)
    private LocalDate freeResetDate;

    public Wallet(Long userId, LocalDate todayKst) {
        this.userId = userId;
        this.freeResetDate = todayKst;
    }

    /** KST 날짜가 바뀌었으면 무료 엽서 사용량을 리셋한다. */
    public void resetFreeIfNewDay(LocalDate todayKst) {
        if (!todayKst.equals(freeResetDate)) {
            this.freeResetDate = todayKst;
            this.freePostcardsUsedToday = 0;
        }
    }

    public int freePostcardsLeft(int dailyFree) {
        return Math.max(0, dailyFree - freePostcardsUsedToday);
    }

    /** 무료 잔여가 있으면 1장 사용하고 true. */
    public boolean tryUseFreePostcard(int dailyFree) {
        if (freePostcardsUsedToday >= dailyFree) {
            return false;
        }
        freePostcardsUsedToday++;
        return true;
    }

    public boolean trySpendPostcard() {
        if (postcardBalance < 1) {
            return false;
        }
        postcardBalance--;
        return true;
    }

    public boolean trySpendStamp() {
        if (stampBalance < 1) {
            return false;
        }
        stampBalance--;
        return true;
    }

    public boolean trySpendBookmarks(int amount) {
        if (amount < 1 || bookmarkBalance < amount) {
            return false;
        }
        bookmarkBalance -= amount;
        return true;
    }

    public void add(int bookmarks, int postcards, int stamps) {
        this.bookmarkBalance += bookmarks;
        this.postcardBalance += postcards;
        this.stampBalance += stamps;
    }
}
