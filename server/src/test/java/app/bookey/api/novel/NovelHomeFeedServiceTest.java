package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelHomeFeedServiceTest {
    private static final Long ME = 1L;
    private static final Instant NOON = Instant.parse("2026-10-15T03:00:00Z");          // KST 10-15 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NEXT_MIDNIGHT = Instant.parse("2026-10-15T15:00:00Z"); // KST 10-16 00:00

    private final NovelHomeFeedRepository feeds = mock(NovelHomeFeedRepository.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelLikeRepository likes = mock(NovelLikeRepository.class);
    private final NovelDeliveryService delivery = mock(NovelDeliveryService.class);
    private final Clock clock = mock(Clock.class);
    private final NovelService novelService = new NovelService(novels, mock(NovelMemberRepository.class), chapters,
            mock(NovelDraftRepository.class), mock(NovelCoverRepository.class), likes, mock(UserRepository.class),
            mock(RateLimiter.class), mock(NovelDeliveryRepository.class), delivery, mock(NovelBaseWorkRepository.class),
            mock(UserFollowRepository.class), mock(NovelSunlightService.class));
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            null, null, new BookeyProperties.Novel(2, 3, 7, null, null));
    private final NovelHomeFeedService service =
            new NovelHomeFeedService(feeds, chapters, novelService, properties, clock);
    /** 자리가 다 찼거나 지워져 더는 이어 쓸 수 없게 된 회차. */
    private final Set<Long> gone = new HashSet<>();
    /** 회차가 어느 작품의 것인지. 따로 정하지 않으면 회차마다 다른 작품이다. */
    private final Map<Long, Long> novelOf = new HashMap<>();

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(NOON);
        // DB 처럼 id 순으로 돌려준다 — 저장된 순서로 다시 세우는지 본다.
        when(chapters.continuableAmong(eq(ME), anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(1);
            return ids.stream().filter(id -> !gone.contains(id)).sorted().toList();
        });
        when(chapters.findAllById(anyIterable())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).sorted().map(this::chapter).toList();
        });
        when(novels.findAllById(anyIterable())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).map(this::novel).toList();
        });
    }

    @Test @DisplayName("처음 열면 이어 쓸 수 있는 회차를 열매로 골라 첫 묶음을 놓고 보여 준 때를 남긴다 — 횟수는 쓰지 않는다")
    void firstVisitPlacesPickedFruits() {
        NovelHomeFeed created = new NovelHomeFeed(ME, TODAY);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(created));
        when(feeds.insertIfAbsent(ME, TODAY)).thenReturn(1);
        List<Long> picked = List.of(9L, 8L, 7L, 6L, 4L, 2L, 1L);
        when(chapters.pickHomeFruits(ME, NOON.minus(Duration.ofDays(7)), NOON.minus(Duration.ofDays(1)),
                NOON.minus(Duration.ofDays(7)), 7)).thenReturn(picked);

        NovelHomeFeedView view = service.current(ME);

        verify(feeds).insertIfAbsent(ME, TODAY);
        assertThat(ids(view)).isEqualTo(picked);
        assertThat(created.getChapterIds()).isEqualTo(picked);
        picked.forEach(id -> verify(chapters).markSeen(ME, id, NOON));
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(2);
        assertThat(view.dailyFreeRefreshes()).isEqualTo(2);
        assertThat(view.resetsAt()).isEqualTo(NEXT_MIDNIGHT);
        assertThat(view.adRefreshesLeftToday()).isEqualTo(3);
        assertThat(view.dailyAdRefreshes()).isEqualTo(3);
        assertThat(view.paidRefreshes()).isZero();
    }

    @Test @DisplayName("열매에는 회차와 그 작품 요약, 남은 가지 자리가 실린다")
    void fruitCarriesChapterNovelAndSlots() {
        storedFeed(List.of(7L), 0, TODAY);
        when(delivery.freeSlots(any(), argThat(c -> c != null && c.getId() == 7L))).thenReturn(2);

        NovelHomeFruitView fruit = service.current(ME).fruits().get(0);

        assertThat(fruit.chapterId()).isEqualTo(7L);
        assertThat(fruit.chapterNumber()).isEqualTo(2);
        assertThat(fruit.chapterTitle()).isEqualTo("회차 7");
        assertThat(fruit.slotsLeft()).isEqualTo(2);
        assertThat(fruit.novel().id()).isEqualTo(1007L);
        assertThat(fruit.novel().mode()).isEqualTo(NovelMode.OPEN);
    }

    @Test @DisplayName("같은 작품의 다른 회차도 따로 열매가 되고, 예전 앱이 읽는 작품 목록에는 한 번만 실린다")
    void sameNovelMayDropManyFruits() {
        novelOf.put(3L, 50L);
        novelOf.put(5L, 50L);
        storedFeed(List.of(3L, 5L, 9L), 0, TODAY);

        NovelHomeFeedView view = service.current(ME);

        assertThat(ids(view)).containsExactly(3L, 5L, 9L);
        assertThat(view.novels()).extracting(NovelSummary::id).containsExactly(50L, 1009L);
    }

    @Test @DisplayName("처음 열 때 다른 요청이 먼저 묶음을 만들었으면 덮어쓰지 않고 그 묶음을 보여 준다")
    void firstVisitKeepsBatchMadeByConcurrentRequest() {
        NovelHomeFeed madeByOther = new NovelHomeFeed(ME, TODAY);
        madeByOther.placeFirst(List.of(4L, 2L));
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(madeByOther));
        when(feeds.insertIfAbsent(ME, TODAY)).thenReturn(0);

        assertThat(ids(service.current(ME))).containsExactly(4L, 2L);
        assertThat(madeByOther.getChapterIds()).containsExactly(4L, 2L);
        verify(chapters, never()).pickHomeFruits(any(), any(), any(), any(), anyInt());
    }

    @Test @DisplayName("저장된 순서대로 보여 주되 더 이어 쓸 수 없게 된 회차는 빼고, 빈자리는 새로 채우지 않는다")
    void currentSkipsGoneFruitsWithoutRefilling() {
        storedFeed(List.of(7L, 3L, 5L, 1L), 0, TODAY);
        gone.add(5L);

        assertThat(ids(service.current(ME))).containsExactly(7L, 3L, 1L);
        verify(chapters, never()).pickHomeFruits(any(), any(), any(), any(), anyInt());
        verify(feeds, never()).insertIfAbsent(any(), any());
    }

    @Test @DisplayName("놓인 열매가 모두 더 이어 쓸 수 없게 됐으면 무료 횟수를 쓰지 않고 새 묶음을 놓는다")
    void allGoneBatchIsReplacedForFree() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L), 1, TODAY);
        gone.addAll(List.of(1L, 2L));
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(5L, 6L));

        NovelHomeFeedView view = service.current(ME);

        assertThat(ids(view)).containsExactly(5L, 6L);
        assertThat(feed.getChapterIds()).containsExactly(5L, 6L);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
    }

    @Test @DisplayName("일주일 안에 다 보여 준 뒤라 새 열매가 없으면, 밭이 비지 않게 보여 준 적 있는 열매라도 놓는다")
    void firstBatchFallsBackToSeenFruits() {
        NovelHomeFeed feed = storedFeed(List.of(), 0, TODAY);
        when(chapters.pickHomeFruits(eq(ME), eq(NOON.minus(Duration.ofDays(7))), any(), any(), eq(7))).thenReturn(List.of());
        when(chapters.pickHomeFruits(eq(ME), eq(NOON), any(), any(), eq(7))).thenReturn(List.of(3L));

        assertThat(ids(service.current(ME))).containsExactly(3L);
        assertThat(feed.getChapterIds()).containsExactly(3L);
    }

    @Test @DisplayName("열매 작품에도 피드처럼 좋아요 수와 내가 누른 작품이 실린다")
    void fruitsCarryLikesLikeFeed() {
        storedFeed(List.of(7L, 3L), 0, TODAY);
        when(likes.countPerNovel(anyCollection())).thenReturn(List.of(likeCount(1007L, 120L)));
        when(likes.findAllByUserIdAndNovelIdIn(eq(ME), anyCollection()))
                .thenReturn(List.of(NovelLike.builder().userId(ME).novelId(1003L).build()));

        List<NovelHomeFruitView> shown = service.current(ME).fruits();

        assertThat(shown.get(0).novel().likeCount()).isEqualTo(120L);
        assertThat(shown.get(0).novel().liked()).isFalse();
        assertThat(shown.get(1).novel().likeCount()).isZero();
        assertThat(shown.get(1).novel().liked()).isTrue();
    }

    @Test @DisplayName("새로고침은 무료 횟수 하나를 쓰고, 일주일 안에 보여 준 적 없는 열매로 바꾼다")
    void refreshReplacesWithUnseenFruits() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), 0, TODAY);
        List<Long> fresh = List.of(31L, 32L, 33L, 34L, 35L, 36L, 37L);
        when(chapters.pickHomeFruits(ME, NOON.minus(Duration.ofDays(7)), NOON.minus(Duration.ofDays(1)),
                NOON.minus(Duration.ofDays(7)), 7)).thenReturn(fresh);

        NovelHomeFeedView view = service.refresh(ME);

        assertThat(ids(view)).isEqualTo(fresh);
        assertThat(feed.getChapterIds()).isEqualTo(fresh);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
        fresh.forEach(id -> verify(chapters).markSeen(ME, id, NOON));
    }

    @Test @DisplayName("무료 두 번을 다 쓰고 결제한 횟수도 없으면 세 번째 새로고침은 429로 막고 묶음을 그대로 둔다 — 광고 횟수는 쓰지 않는다")
    void thirdRefreshIsRejected() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 0, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L), List.of(12L));
        service.refresh(ME);
        service.refresh(ME);
        List<Long> afterSecond = feed.getChapterIds();

        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT);
        assertThat(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT.getStatus().value()).isEqualTo(429);
        assertThat(feed.getChapterIds()).isEqualTo(afterSecond);
        assertThat(feed.freeQuota(2).leftOn(TODAY)).isZero();
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(3);
        verify(chapters, times(2)).pickHomeFruits(eq(ME), any(), any(), any(), eq(7));
    }

    @Test @DisplayName("새로 놓을 열매가 없으면 무료 횟수를 쓰지 않고 409로 알린다")
    void refreshWithoutNewFruitsKeepsQuota() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L), 1, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of());

        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_FEED_NO_NEW);
        assertThat(feed.freeQuota(2).leftOn(TODAY)).isEqualTo(1);
        assertThat(feed.getChapterIds()).containsExactly(1L, 2L);
        verify(chapters, never()).markSeen(any(), any(), any());
    }

    @Test @DisplayName("무료가 남았으면 결제한 횟수가 있어도 무료를 먼저 쓴다")
    void refreshPrefersFreeOverPaid() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 1, 0, 1, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L));

        NovelHomeFeedView view = service.refresh(ME);

        assertThat(view.freeRefreshesLeftToday()).isZero();
        assertThat(view.paidRefreshes()).isEqualTo(1);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
    }

    @Test @DisplayName("무료를 다 쓰면 결제한 횟수를 하나씩 쓰고, 그것도 다 쓰면 429 — 남은 광고 횟수는 건드리지 않는다")
    void refreshUsesPaidAfterFreeThenRejects() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 2, 0, 2, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L), List.of(12L));

        assertThat(service.refresh(ME).paidRefreshes()).isEqualTo(1);
        NovelHomeFeedView second = service.refresh(ME);

        assertThat(ids(second)).startsWith(12L);
        assertThat(second.paidRefreshes()).isZero();
        assertThat(second.adRefreshesLeftToday()).isEqualTo(3);
        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT);
        assertThat(feed.getPaidRefreshes()).isZero();
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(3);
    }

    @Test @DisplayName("광고를 보고 새로고침해도 무료가 남았으면 무료를 쓰고 광고 횟수는 아낀다")
    void adRefreshUsesFreeFirst() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 1, 0, 0, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L));

        NovelHomeFeedView view = service.refreshWithAd(ME);

        assertThat(ids(view)).startsWith(11L);
        assertThat(view.freeRefreshesLeftToday()).isZero();
        assertThat(view.adRefreshesLeftToday()).isEqualTo(3);
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(3);
    }

    @Test @DisplayName("무료를 다 쓰면 광고로 하루 세 번까지 새로고침하고, 네 번째는 NOVEL_REFRESH_AD_LIMIT(429)로 막는다 — 결제한 횟수는 쓰지 않는다")
    void adRefreshUpToDailyAdThenRejects() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 2, 0, 1, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7)))
                .thenReturn(List.of(11L), List.of(12L), List.of(13L));

        assertThat(service.refreshWithAd(ME).adRefreshesLeftToday()).isEqualTo(2);
        assertThat(service.refreshWithAd(ME).adRefreshesLeftToday()).isEqualTo(1);
        NovelHomeFeedView third = service.refreshWithAd(ME);
        List<Long> afterThird = feed.getChapterIds();

        assertThat(third.adRefreshesLeftToday()).isZero();
        assertThat(third.paidRefreshes()).isEqualTo(1);
        assertThatThrownBy(() -> service.refreshWithAd(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_REFRESH_AD_LIMIT);
        assertThat(ErrorCode.NOVEL_REFRESH_AD_LIMIT.getStatus().value()).isEqualTo(429);
        assertThat(feed.getChapterIds()).isEqualTo(afterThird);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        verify(chapters, times(3)).pickHomeFruits(eq(ME), any(), any(), any(), eq(7));
    }

    @Test @DisplayName("새로 놓을 열매가 없으면 광고 횟수도 결제한 횟수도 쓰지 않는다")
    void noNewFruitsKeepsAdAndPaid() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L), 2, 1, 1, TODAY);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of());

        assertThatThrownBy(() -> service.refreshWithAd(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_FEED_NO_NEW);
        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_FEED_NO_NEW);
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(2);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        assertThat(feed.getChapterIds()).containsExactly(1L, 2L);
    }

    @Test @DisplayName("새 열매가 모자라면 남는 자리를 이전 묶음에서 채운다 — 더 이어 쓸 수 없게 된 회차는 빼고")
    void shortRefreshKeepsSomeOfPrevious() {
        storedFeed(LongStream.rangeClosed(1, 7).boxed().toList(), 0, TODAY);
        gone.add(2L);
        gone.add(3L);
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(40L, 41L));

        List<Long> shown = ids(service.refresh(ME));

        assertThat(shown).hasSize(7).startsWith(40L, 41L);
        assertThat(shown.subList(2, 7)).containsExactlyInAnyOrder(1L, 4L, 5L, 6L, 7L);
    }

    @Test @DisplayName("KST 자정이 지나면 무료 횟수가 다시 찬다")
    void quotaRefillsAfterKstMidnight() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 2, TODAY);
        assertThat(service.current(ME).freeRefreshesLeftToday()).isZero();

        when(clock.instant()).thenReturn(NEXT_MIDNIGHT.plusSeconds(60));
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L));
        assertThat(service.current(ME).freeRefreshesLeftToday()).isEqualTo(2);
        NovelHomeFeedView refreshed = service.refresh(ME);

        assertThat(refreshed.freeRefreshesLeftToday()).isEqualTo(1);
        assertThat(refreshed.resetsAt()).isEqualTo(Instant.parse("2026-10-16T15:00:00Z"));
        assertThat(feed.getFreeResetDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test @DisplayName("KST 자정이 지나면 무료·광고 횟수가 함께 다시 차고, 결제한 횟수는 그대로 남는다")
    void freeAndAdRefillTogetherAfterKstMidnight() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 2, 3, 1, TODAY);
        NovelHomeFeedView usedUp = service.current(ME);
        assertThat(usedUp.freeRefreshesLeftToday()).isZero();
        assertThat(usedUp.adRefreshesLeftToday()).isZero();

        when(clock.instant()).thenReturn(NEXT_MIDNIGHT.plusSeconds(60));
        NovelHomeFeedView nextDay = service.current(ME);
        assertThat(nextDay.freeRefreshesLeftToday()).isEqualTo(2);
        assertThat(nextDay.adRefreshesLeftToday()).isEqualTo(3);
        assertThat(nextDay.paidRefreshes()).isEqualTo(1);

        // 새 날 광고를 보면 무료를 먼저 쓰고, 어제 광고 횟수는 오늘로 넘어오지 않는다.
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L));
        NovelHomeFeedView refreshed = service.refreshWithAd(ME);
        assertThat(refreshed.freeRefreshesLeftToday()).isEqualTo(1);
        assertThat(refreshed.adRefreshesLeftToday()).isEqualTo(3);
        assertThat(refreshed.paidRefreshes()).isEqualTo(1);
        assertThat(feed.getFreeResetDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test @DisplayName("묶음이 아직 없을 때 새로고침하면 빈 묶음을 만들어 잠근 뒤 센다")
    void refreshCreatesMissingRow() {
        NovelHomeFeed created = new NovelHomeFeed(ME, TODAY);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(created));
        when(chapters.pickHomeFruits(eq(ME), any(), any(), any(), eq(7))).thenReturn(List.of(11L, 12L));

        NovelHomeFeedView view = service.refresh(ME);

        verify(feeds).insertIfAbsent(ME, TODAY);
        assertThat(ids(view)).containsExactly(11L, 12L);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
    }

    private NovelHomeFeed storedFeed(List<Long> ids, int freeUsed, LocalDate countedOn) {
        return storedFeed(ids, freeUsed, 0, 0, countedOn);
    }
    private NovelHomeFeed storedFeed(List<Long> ids, int freeUsed, int adUsed, int paid, LocalDate countedOn) {
        NovelHomeFeed feed = new NovelHomeFeed(ME, countedOn);
        feed.placeFirst(ids);
        ReflectionTestUtils.setField(feed, "freeUsedToday", (short) freeUsed);
        ReflectionTestUtils.setField(feed, "adUsedToday", (short) adUsed);
        ReflectionTestUtils.setField(feed, "paidRefreshes", paid);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.of(feed));
        return feed;
    }
    /** 회차 id 마다 다른 사람이 쓴 2장. 따로 정하지 않으면 작품 id 는 1000 + 회차 id 다. */
    private NovelChapter chapter(long id) {
        NovelChapter c = new NovelChapter(novelOf.getOrDefault(id, 1000 + id), 2L, 90L, 2, "회차 " + id, "본문 " + id);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "createdAt", NOON);
        return c;
    }
    private Novel novel(long id) {
        Novel novel = Novel.open(2L, "작품 " + id, "소개", "판타지", 10, 24, 3, null);
        ReflectionTestUtils.setField(novel, "id", id);
        ReflectionTestUtils.setField(novel, "createdAt", NOON);
        return novel;
    }
    private static List<Long> ids(NovelHomeFeedView view) {
        return view.fruits().stream().map(NovelHomeFruitView::chapterId).toList();
    }
    private static NovelLikeRepository.NovelLikeCount likeCount(Long novelId, long count) {
        return new NovelLikeRepository.NovelLikeCount() {
            public Long getNovelId() { return novelId; }
            public long getLikeCount() { return count; }
        };
    }
}
