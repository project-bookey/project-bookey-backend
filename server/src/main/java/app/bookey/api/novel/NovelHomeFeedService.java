package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelHomeFeedView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 열매 밭의 피드 자리. 앱은 받은 열매 → 내 차례 → 이 묶음 순으로 열매를 놓는다.
 * 묶음은 사람마다 저장해 두고, 비어 있을 때 말고는 새로고침할 때만 바꾼다.
 * 새로고침은 하루 무료 횟수 → 광고를 보고 받는 하루 횟수 → 결제한 횟수 순으로 쓴다. 무료·광고 횟수는 KST 자정에 다시 차고,
 * 결제한 횟수는 날이 바뀌어도 남는다. 결제 주문·확인은 {@link NovelRefreshPurchaseService} 가 맡는다.
 */
@Service @RequiredArgsConstructor
public class NovelHomeFeedService {
    private final NovelHomeFeedRepository feeds;
    private final NovelRepository novels;
    private final NovelDeliveryRepository deliveries;
    private final NovelService novelService;
    private final BookeyProperties properties;
    private final Clock clock;

    /**
     * 지금 놓인 묶음. 처음 열었거나 묶음이 비어 있으면(그때 놓을 작품이 없었던 경우) 첫 묶음 규칙으로 놓는다.
     * 횟수는 쓰지 않는다. 동시에 들어온 새로고침의 묶음을 덮어쓰지 않게 행을 잠그고 읽는다 —
     * 잠그지 않고 먼저 읽어 두면 나중에 잠가도 이미 읽은 엔티티는 새로 읽히지 않는다.
     */
    @Transactional
    public NovelHomeFeedView current(Long me) {
        Instant now = clock.instant();
        NovelHomeFeed feed = lockFeed(me, NovelRefreshQuota.today(now));
        if (feed.getNovelIds().isEmpty()) feed.placeFirst(firstBatch(me, now));
        return view(me, feed, now);
    }

    /**
     * 새로고침 버튼 — 오늘 무료 횟수가 남았으면 무료로, 다 썼으면 결제한 횟수로 새 묶음으로 바꾼다.
     * 광고 횟수는 광고를 본 뒤({@link #refreshWithAd})에만 쓴다. 새로 놓을 작품이 없으면 횟수를 쓰지 않고 거절한다.
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
        return view(me, feed, now);
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
        return view(me, feed, now);
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
     * 그때 새로 놓을 작품이 없어 거절돼도 결제한 횟수는 다음에 쓸 수 있게 남는다.
     */
    @Transactional
    public NovelHomeFeedView addPaidRefresh(Long me) {
        Instant now = clock.instant();
        NovelHomeFeed feed = lockFeed(me, NovelRefreshQuota.today(now));
        feed.addPaidRefresh();
        return view(me, feed, now);
    }

    /**
     * 다음 묶음 — 지금 묶음·받은 열매·내 차례 작품을 뺀 새 작품을 앞에 놓고, 모자란 자리는 이전 묶음에서 채운다.
     * 새로 놓을 작품이 없으면 횟수를 쓰기 전에 거절한다.
     */
    private List<Long> nextBatch(Long me, NovelHomeFeed feed, Instant now) {
        List<Long> previous = feed.getNovelIds();
        Set<Long> placedAhead = placedAhead(me, now);
        Set<Long> excluded = new HashSet<>(previous);
        excluded.addAll(placedAhead);
        List<Long> picked = novels.pickForHomeFeed(me, excluded, feedSize());
        if (picked.isEmpty()) throw ApiException.of(ErrorCode.NOVEL_FEED_NO_NEW);

        List<Long> keepable = showableInOrder(previous).stream()
                .map(Novel::getId)
                .filter(id -> !placedAhead.contains(id))
                .toList();
        return NovelHomeFeedBatch.refreshed(picked, keepable, feedSize(), ThreadLocalRandom.current());
    }

    /** 첫 묶음 규칙 — 지금 피드 순서(최신순)에서 받은 열매·내 차례 작품을 뺀 앞쪽 자리 수만큼. */
    private List<Long> firstBatch(Long me, Instant now) {
        Set<Long> placedAhead = placedAhead(me, now);
        // 뺄 작품 수만큼 더 읽어야 걸러 낸 뒤에도 자리를 다 채울 수 있다.
        PageRequest latestPage = PageRequest.of(0, feedSize() + placedAhead.size());
        List<Long> latestFirst = novels.feed(NovelKind.RELAY, null, latestPage).map(Novel::getId).getContent();
        return NovelHomeFeedBatch.first(latestFirst, placedAhead, feedSize());
    }

    /** 행을 잠가 가져온다. 아직 없으면 빈 묶음으로 만든 뒤 잠근다. */
    private NovelHomeFeed lockFeed(Long me, LocalDate today) {
        return feeds.lockByUserId(me).orElseGet(() -> {
            feeds.insertIfAbsent(me, today);
            return feeds.lockByUserId(me).orElseThrow();
        });
    }

    /** 홈에서 이 묶음보다 먼저 놓이는 작품 — 내가 든 열매(배달)의 작품과 내 차례인 작품. 앱이 받는 목록과 같은 범위다. */
    private Set<Long> placedAhead(Long me, Instant now) {
        Set<Long> ids = new HashSet<>();
        deliveries.holding(me, now, NovelService.HOME_LIST_PAGE).forEach(held -> ids.add(held.getNovelId()));
        novels.myTurns(me, now, NovelService.HOME_LIST_PAGE).forEach(novel -> ids.add(novel.getId()));
        return ids;
    }

    /** 저장된 순서대로, 아직 보여 줄 수 있는 작품만 — 빠진 자리를 새로 채우지는 않는다. */
    private List<Novel> showableInOrder(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Novel> byId = novels.findShowableOnHome(ids).stream()
                .collect(Collectors.toMap(Novel::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private NovelHomeFeedView view(Long me, NovelHomeFeed feed, Instant now) {
        LocalDate today = NovelRefreshQuota.today(now);
        return new NovelHomeFeedView(novelService.summaries(showableInOrder(feed.getNovelIds()), me),
                feed.freeQuota(dailyFree()).leftOn(today), dailyFree(), NovelRefreshQuota.nextResetAfter(now),
                feed.adQuota(dailyAd()).leftOn(today), dailyAd(), feed.getPaidRefreshes());
    }

    private int dailyFree() { return properties.novel().feedRefreshDailyFree(); }

    private int dailyAd() { return properties.novel().feedRefreshDailyAd(); }

    private int feedSize() { return properties.novel().homeFeedSize(); }
}
