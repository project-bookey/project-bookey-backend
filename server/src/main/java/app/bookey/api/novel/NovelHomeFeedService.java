package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelHomeFeedView;
import app.bookey.api.novel.dto.NovelDtos.NovelHomeFruitView;
import app.bookey.api.novel.dto.NovelDtos.NovelSummary;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 홈 열매 밭의 피드 자리. 열매 하나는 지금 이어 쓸 수 있는 회차 하나다(2026-10-21 사용자 결정 — 무작위 배달 대신).
 * 앱은 내가 자리를 맡은 열매 → 내 차례 → 이 묶음 순으로 놓는다. 묶음은 사람마다 저장해 두고 새로고침할 때 바꾼다.
 * 열매는 최근에 쓴 회차 → 관심 장르가 맞는 작품의 회차 순으로, 같은 묶음 안에서는 무작위로 고르고,
 * 새로고침하면 일주일 안에 보여 준 적 없는 회차로 바꾼다.
 * 새로고침은 하루 무료 횟수 → 광고를 보고 받는 하루 횟수 → 결제한 횟수 순으로 쓴다. 무료·광고 횟수는 KST 자정에 다시 차고,
 * 결제한 횟수는 날이 바뀌어도 남는다. 결제 주문·확인은 {@link NovelRefreshPurchaseService} 가 맡는다.
 */
@Service @RequiredArgsConstructor
public class NovelHomeFeedService {
    /** 이 안에 보여 준 열매는 새로고침해도 다시 놓지 않는다. */
    static final Duration SEEN_FOR = Duration.ofDays(7);
    /** 열매를 고를 때 가장 먼저 놓는 '방금 쓴' 회차와 그다음 '최근에 쓴' 회차의 범위. */
    static final Duration FRESH_FOR = Duration.ofDays(1);
    static final Duration RECENT_FOR = Duration.ofDays(7);
    private final NovelHomeFeedRepository feeds;
    private final NovelChapterRepository chapters;
    private final NovelService novelService;
    private final BookeyProperties properties;
    private final Clock clock;

    /**
     * 지금 놓인 묶음. 처음 열었거나 놓인 열매가 모두 더 이어 쓸 수 없게 됐으면 새 묶음을 놓는다 — 횟수는 쓰지 않는다.
     * 그때는 보여 준 적 있는 열매도 다시 놓을 수 있다(밭이 비지 않게). 동시에 들어온 새로고침의 묶음을 덮어쓰지 않게 행을 잠그고 읽는다.
     */
    @Transactional
    public NovelHomeFeedView current(Long me) {
        Instant now = clock.instant();
        NovelHomeFeed feed = lockFeed(me, NovelRefreshQuota.today(now));
        List<Long> placed = continuableInOrder(me, feed.getChapterIds());
        if (placed.isEmpty()) {
            List<Long> first = pick(me, now, now.minus(SEEN_FOR));
            if (first.isEmpty()) first = pick(me, now, now);
            feed.placeFirst(first);
            markSeen(me, first, now);
            placed = first;
        }
        return view(me, feed, placed, now);
    }

    /**
     * 새로고침 버튼 — 오늘 무료 횟수가 남았으면 무료로, 다 썼으면 결제한 횟수로 새 묶음으로 바꾼다.
     * 광고 횟수는 광고를 본 뒤({@link #refreshWithAd})에만 쓴다. 새로 놓을 열매가 없으면 횟수를 쓰지 않고 거절한다.
     */
    @Transactional
    public NovelHomeFeedView refresh(Long me) {
        Instant now = clock.instant();
        LocalDate today = NovelRefreshQuota.today(now);
        NovelHomeFeed feed = lockFeed(me, today);
        boolean free = feed.freeQuota(dailyFree()).canRefreshOn(today);
        if (!free && feed.getPaidRefreshes() == 0) throw ApiException.of(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT);

        List<Long> next = nextBatch(me, feed, now);
        if (free) feed.refreshWithFree(next, dailyFree(), today);
        else feed.refreshWithPaid(next);
        return view(me, feed, next, now);
    }

    /**
     * 광고를 끝까지 본 뒤 부르는 새로고침. 무료 횟수가 아직 남았으면 광고 횟수를 아끼고 무료를 쓰고,
     * 없으면 오늘 광고 횟수 하나를 쓴다. 광고 횟수도 다 쓰면 거절한다 — 그다음은 결제다.
     * 지금 앱의 광고는 목업이라 서버는 '광고를 봤다'는 요청을 그대로 믿는다. 보상형 광고 SDK 를 붙이면
     * 광고 서버가 보내는 보상 확인(예: AdMob SSV 콜백)을 서버에서 검증한 뒤에만 광고 횟수를 주도록 바꾼다.
     */
    @Transactional
    public NovelHomeFeedView refreshWithAd(Long me) {
        Instant now = clock.instant();
        LocalDate today = NovelRefreshQuota.today(now);
        NovelHomeFeed feed = lockFeed(me, today);
        boolean free = feed.freeQuota(dailyFree()).canRefreshOn(today);
        if (!free && !feed.adQuota(dailyAd()).canRefreshOn(today)) throw ApiException.of(ErrorCode.NOVEL_REFRESH_AD_LIMIT);

        List<Long> next = nextBatch(me, feed, now);
        if (free) feed.refreshWithFree(next, dailyFree(), today);
        else feed.refreshWithAd(next, dailyAd(), today);
        return view(me, feed, next, now);
    }

    /** 결제 주문을 열기 전에 — 오늘 무료·광고 횟수를 다 썼고 결제해 둔 횟수도 없어야 한다. 남은 몫을 두고 사지 않게 막는다. */
    @Transactional
    public void requireRefreshesUsedUp(Long me) {
        LocalDate today = NovelRefreshQuota.today(clock.instant());
        NovelHomeFeed feed = lockFeed(me, today);
        if (feed.freeQuota(dailyFree()).canRefreshOn(today) || feed.adQuota(dailyAd()).canRefreshOn(today)
                || feed.getPaidRefreshes() > 0) {
            throw ApiException.of(ErrorCode.NOVEL_REFRESH_STILL_LEFT);
        }
    }

    /**
     * 확인한 결제 하나만큼 새로고침을 더한다. 묶음은 바꾸지 않는다 — 앱이 이어서 {@link #refresh} 를 부르고,
     * 그때 새로 놓을 열매가 없어 거절돼도 결제한 횟수는 다음에 쓸 수 있게 남는다.
     */
    @Transactional
    public NovelHomeFeedView addPaidRefresh(Long me) {
        Instant now = clock.instant();
        NovelHomeFeed feed = lockFeed(me, NovelRefreshQuota.today(now));
        feed.addPaidRefresh();
        return view(me, feed, continuableInOrder(me, feed.getChapterIds()), now);
    }

    /**
     * 다음 묶음 — 일주일 안에 보여 준 적 없는 열매를 앞에 놓고, 모자란 자리는 지금 묶음에서 아직 이어 쓸 수 있는 열매로 채운다.
     * 새로 놓을 열매가 없으면 횟수를 쓰기 전에 거절한다.
     */
    private List<Long> nextBatch(Long me, NovelHomeFeed feed, Instant now) {
        List<Long> picked = pick(me, now, now.minus(SEEN_FOR));
        if (picked.isEmpty()) throw ApiException.of(ErrorCode.NOVEL_FEED_NO_NEW);
        List<Long> keepable = continuableInOrder(me, feed.getChapterIds());
        markSeen(me, picked, now);
        return NovelHomeFeedBatch.refreshed(picked, keepable, feedSize(), ThreadLocalRandom.current());
    }

    /** seenSince 뒤에 보여 준 열매를 빼고 고른다. seenSince 를 지금으로 주면 보여 준 적 있는 열매도 고른다. */
    private List<Long> pick(Long me, Instant now, Instant seenSince) {
        return chapters.pickHomeFruits(me, seenSince, now.minus(FRESH_FOR), now.minus(RECENT_FOR), feedSize());
    }

    private void markSeen(Long me, List<Long> chapterIds, Instant now) {
        chapterIds.forEach(id -> chapters.markSeen(me, id, now));
    }

    /** 저장된 순서대로, 아직 이어 쓸 수 있는 열매만 — 빠진 자리를 새로 채우지는 않는다. */
    private List<Long> continuableInOrder(Long me, List<Long> chapterIds) {
        Set<Long> continuable = new HashSet<>(chapters.continuableAmong(me, chapterIds));
        return chapterIds.stream().filter(continuable::contains).toList();
    }

    /** 행을 잠가 가져온다. 아직 없으면 빈 묶음으로 만든 뒤 잠근다. */
    private NovelHomeFeed lockFeed(Long me, LocalDate today) {
        return feeds.lockByUserId(me).orElseGet(() -> {
            feeds.insertIfAbsent(me, today);
            return feeds.lockByUserId(me).orElseThrow();
        });
    }

    private NovelHomeFeedView view(Long me, NovelHomeFeed feed, List<Long> chapterIds, Instant now) {
        LocalDate today = NovelRefreshQuota.today(now);
        List<NovelHomeFruitView> fruits = novelService.fruits(chapterIds, me);
        return new NovelHomeFeedView(novelsOf(fruits),
                feed.freeQuota(dailyFree()).leftOn(today), dailyFree(), NovelRefreshQuota.nextResetAfter(now),
                feed.adQuota(dailyAd()).leftOn(today), dailyAd(), feed.getPaidRefreshes(), fruits);
    }

    /** 예전 앱이 읽는 작품 목록 — 열매의 작품을 한 번씩, 열매 순서대로. */
    private static List<NovelSummary> novelsOf(List<NovelHomeFruitView> fruits) {
        Map<Long, NovelSummary> byId = new LinkedHashMap<>();
        fruits.forEach(fruit -> byId.putIfAbsent(fruit.novel().id(), fruit.novel()));
        return List.copyOf(byId.values());
    }

    private int dailyFree() { return properties.novel().feedRefreshDailyFree(); }

    private int dailyAd() { return properties.novel().feedRefreshDailyAd(); }

    private int feedSize() { return properties.novel().homeFeedSize(); }
}
