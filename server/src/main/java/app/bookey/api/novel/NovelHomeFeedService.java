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
 * 묶음은 사람마다 저장해 두고, 비어 있을 때 말고는 새로고침할 때만 바꾼다. 하루 무료 횟수는 KST 자정에 다시 찬다.
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
     * 무료 횟수는 쓰지 않는다. 동시에 들어온 새로고침의 묶음을 덮어쓰지 않게 행을 잠그고 읽는다 —
     * 잠그지 않고 먼저 읽어 두면 나중에 잠가도 이미 읽은 엔티티는 새로 읽히지 않는다.
     */
    @Transactional
    public NovelHomeFeedView current(Long me) {
        Instant now = clock.instant();
        NovelHomeFeed feed = lockFeed(me, NovelRefreshQuota.today(now));
        if (feed.getNovelIds().isEmpty()) feed.placeFirst(firstBatch(me, now));
        return view(me, feed, now);
    }

    /** 오늘 무료 횟수 하나를 쓰고 새 묶음으로 바꾼다. 새로 놓을 작품이 없으면 횟수를 쓰지 않고 거절한다. */
    @Transactional
    public NovelHomeFeedView refresh(Long me) {
        Instant now = clock.instant();
        LocalDate today = NovelRefreshQuota.today(now);
        NovelHomeFeed feed = lockFeed(me, today);
        NovelRefreshQuota quota = feed.quota(dailyFree());
        if (!quota.canRefreshOn(today)) throw ApiException.of(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT);

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
        List<Long> next = NovelHomeFeedBatch.refreshed(picked, keepable, feedSize(), ThreadLocalRandom.current());
        feed.refresh(next, dailyFree(), today);
        return view(me, feed, now);
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
        int leftToday = feed.quota(dailyFree()).leftOn(NovelRefreshQuota.today(now));
        return new NovelHomeFeedView(novelService.summaries(showableInOrder(feed.getNovelIds()), me),
                leftToday, dailyFree(), NovelRefreshQuota.nextResetAfter(now));
    }

    private int dailyFree() { return properties.novel().feedRefreshDailyFree(); }

    private int feedSize() { return properties.novel().homeFeedSize(); }
}
