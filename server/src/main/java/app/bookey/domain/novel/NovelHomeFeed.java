package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.List;

/**
 * 홈 열매 밭의 피드 자리 — 지금 놓인 작품 묶음과 새로고침 횟수(오늘 쓴 무료·광고 횟수, 결제하고 아직 쓰지 않은 횟수).
 * 북키 지갑(wallets)과 따로 둔다. 행은 처음 열거나 새로고침할 때 {@link NovelHomeFeedRepository#insertIfAbsent} 로 만든다.
 */
@Getter @Entity @Table(name = "novel_home_feeds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelHomeFeed extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    /** 보여 줄 순서대로 둔 작품 id. 지워졌거나 가려진 작품은 읽을 때 뺀다. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "novel_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] novelIds = new Long[0];
    @Column(name = "free_used_today", nullable = false) private short freeUsedToday;
    /** 무료를 다 쓴 뒤 광고를 보고 쓴 횟수. */
    @Column(name = "ad_used_today", nullable = false) private short adUsedToday;
    /** 무료·광고 횟수를 센 KST 날짜 — 두 횟수가 이 날짜 하나를 같이 쓴다. */
    @Column(name = "free_reset_date", nullable = false) private LocalDate freeResetDate;
    /** 결제하고 아직 쓰지 않은 새로고침. 날이 바뀌어도 남는다. */
    @Column(name = "paid_refreshes", nullable = false) private int paidRefreshes;

    public NovelHomeFeed(Long userId, LocalDate today) {
        this.userId = userId;
        this.freeResetDate = today;
    }

    public List<Long> getNovelIds() { return List.of(novelIds); }

    public NovelRefreshQuota freeQuota(int dailyFree) {
        return new NovelRefreshQuota(dailyFree, freeUsedToday, freeResetDate);
    }

    public NovelRefreshQuota adQuota(int dailyAd) {
        return new NovelRefreshQuota(dailyAd, adUsedToday, freeResetDate);
    }

    /** 처음 놓는 묶음 — 횟수는 쓰지 않는다. */
    public void placeFirst(List<Long> ids) {
        this.novelIds = ids.toArray(Long[]::new);
    }

    /** 새 묶음으로 바꾸고 오늘 무료 횟수 하나를 쓴다. 남은 횟수는 부르는 쪽이 먼저 확인한다. */
    public void refreshWithFree(List<Long> ids, int dailyFree, LocalDate today) {
        countOn(today);
        this.freeUsedToday = (short) freeQuota(dailyFree).useOneOn(today).usedToday();
        this.novelIds = ids.toArray(Long[]::new);
    }

    /** 새 묶음으로 바꾸고 오늘 광고 횟수 하나를 쓴다. 남은 횟수는 부르는 쪽이 먼저 확인한다. */
    public void refreshWithAd(List<Long> ids, int dailyAd, LocalDate today) {
        countOn(today);
        this.adUsedToday = (short) adQuota(dailyAd).useOneOn(today).usedToday();
        this.novelIds = ids.toArray(Long[]::new);
    }

    /** 새 묶음으로 바꾸고 결제한 새로고침 하나를 쓴다. 남은 횟수는 부르는 쪽이 먼저 확인한다. */
    public void refreshWithPaid(List<Long> ids) {
        if (paidRefreshes <= 0) throw new IllegalStateException("결제한 새로고침이 없다");
        this.paidRefreshes--;
        this.novelIds = ids.toArray(Long[]::new);
    }

    /** 확인한 결제 하나만큼 새로고침을 더한다. 묶음은 바꾸지 않는다. */
    public void addPaidRefresh() {
        this.paidRefreshes++;
    }

    /**
     * 날이 바뀌었으면 무료·광고 횟수를 함께 0 으로 돌리고 오늘부터 센다. 두 횟수가 날짜 하나를 같이 쓰므로,
     * 한쪽만 쓰면서 날짜를 오늘로 옮기면 다른 쪽에 남은 어제 횟수가 오늘 쓴 것처럼 읽힌다.
     */
    private void countOn(LocalDate today) {
        if (today.equals(freeResetDate)) return;
        this.freeUsedToday = 0;
        this.adUsedToday = 0;
        this.freeResetDate = today;
    }
}
