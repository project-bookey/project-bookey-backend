package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 햇살 지갑 — 소설 전용 재화. 잔액은 반드시 {@link NovelSunlightTransaction} 원장과 함께 바뀐다(서비스가 보장). */
@Getter @Entity @Table(name = "novel_sunlight_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelSunlightWallet extends BaseTimeEntity {
    @Id @Column(name = "user_id") private Long userId;
    @Column(nullable = false) private int balance;

    public NovelSunlightWallet(Long userId) { this.userId = userId; }

    public boolean trySpend(int amount) {
        if (amount < 1 || balance < amount) return false;
        balance -= amount;
        return true;
    }
    public void add(int amount) { balance += amount; }
}
