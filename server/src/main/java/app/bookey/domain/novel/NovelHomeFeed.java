package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.List;

/**
 * 홈 열매 밭의 피드 자리 — 지금 놓인 작품 묶음과 오늘 쓴 무료 새로고침 횟수. 북키 지갑(wallets)과 따로 둔다.
 * 행은 처음 열거나 새로고침할 때 {@link NovelHomeFeedRepository#insertIfAbsent} 로 만든다.
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
    /** 무료 횟수를 센 KST 날짜. */
    @Column(name = "free_reset_date", nullable = false) private LocalDate freeResetDate;

    public NovelHomeFeed(Long userId, LocalDate today) {
        this.userId = userId;
        this.freeResetDate = today;
    }

    public List<Long> getNovelIds() { return List.of(novelIds); }

    public NovelRefreshQuota quota(int dailyFree) {
        return new NovelRefreshQuota(dailyFree, freeUsedToday, freeResetDate);
    }

    /** 처음 놓는 묶음 — 무료 횟수는 쓰지 않는다. */
    public void placeFirst(List<Long> ids) {
        this.novelIds = ids.toArray(Long[]::new);
    }

    /** 새 묶음으로 바꾸고 오늘 무료 횟수 하나를 쓴다. 남은 횟수는 부르는 쪽이 먼저 확인한다. */
    public void refresh(List<Long> ids, int dailyFree, LocalDate today) {
        NovelRefreshQuota afterUse = quota(dailyFree).useOneOn(today);
        this.novelIds = ids.toArray(Long[]::new);
        this.freeUsedToday = (short) afterUse.usedToday();
        this.freeResetDate = afterUse.countedOn();
    }
}
