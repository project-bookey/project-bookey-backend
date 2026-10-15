package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelHomeFeedServiceTest {
    private static final Long ME = 1L;
    private static final Instant NOON = Instant.parse("2026-10-15T03:00:00Z");          // KST 10-15 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NEXT_MIDNIGHT = Instant.parse("2026-10-15T15:00:00Z"); // KST 10-16 00:00

    private final NovelHomeFeedRepository feeds = mock(NovelHomeFeedRepository.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelLikeRepository likes = mock(NovelLikeRepository.class);
    private final Clock clock = mock(Clock.class);
    private final NovelService novelService = new NovelService(novels, mock(NovelMemberRepository.class),
            mock(NovelChapterRepository.class), mock(NovelDraftRepository.class), mock(NovelCoverRepository.class), likes,
            mock(UserRepository.class), mock(RateLimiter.class), deliveries, mock(NovelDeliveryService.class),
            mock(NovelBaseWorkRepository.class), mock(UserFollowRepository.class));
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            null, null, new BookeyProperties.Novel(2, 7));
    private final NovelHomeFeedService service =
            new NovelHomeFeedService(feeds, novels, deliveries, novelService, properties, clock);
    /** 비공개가 됐거나 지워졌거나 만든 사람이 탈퇴해 보여 줄 수 없는 작품. */
    private final Set<Long> unshowable = new HashSet<>();

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(NOON);
        // DB 처럼 id 순으로 돌려준다 — 저장된 순서로 다시 세우는지 본다.
        when(novels.findShowableOnHome(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return ids.stream().filter(id -> !unshowable.contains(id)).sorted().map(this::novel).toList();
        });
    }

    @Test @DisplayName("처음 열면 최신순 피드에서 받은 열매·내 차례 작품을 뺀 앞쪽 일곱 편으로 첫 묶음을 만들고, 무료 횟수는 쓰지 않는다")
    void firstVisitPlacesLatestFeed() {
        holding(3L);
        myTurns(5L);
        // 뺄 작품 두 편만큼 더 읽는다.
        List<Novel> latestFirst = LongStream.iterate(9, id -> id >= 1, id -> id - 1).mapToObj(this::novel).toList();
        when(novels.feed(NovelKind.RELAY, null, PageRequest.of(0, 9))).thenReturn(new PageImpl<>(latestFirst));
        NovelHomeFeed created = new NovelHomeFeed(ME, TODAY);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(created));
        when(feeds.insertIfAbsent(ME, TODAY)).thenReturn(1);

        NovelHomeFeedView view = service.current(ME);

        verify(feeds).insertIfAbsent(ME, TODAY);
        assertThat(ids(view)).containsExactly(9L, 8L, 7L, 6L, 4L, 2L, 1L);
        assertThat(created.getNovelIds()).containsExactly(9L, 8L, 7L, 6L, 4L, 2L, 1L);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(2);
        assertThat(view.dailyFreeRefreshes()).isEqualTo(2);
        assertThat(view.resetsAt()).isEqualTo(NEXT_MIDNIGHT);
    }

    @Test @DisplayName("처음 열 때 다른 요청이 먼저 묶음을 만들었으면 덮어쓰지 않고 그 묶음을 보여 준다")
    void firstVisitKeepsBatchMadeByConcurrentRequest() {
        NovelHomeFeed madeByOther = new NovelHomeFeed(ME, TODAY);
        madeByOther.placeFirst(List.of(4L, 2L));
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(madeByOther));
        when(feeds.insertIfAbsent(ME, TODAY)).thenReturn(0);

        assertThat(ids(service.current(ME))).containsExactly(4L, 2L);
        assertThat(madeByOther.getNovelIds()).containsExactly(4L, 2L);
        verify(novels, never()).feed(any(), any(), any());
    }

    @Test @DisplayName("저장된 묶음이 비어 있으면 잠근 채 첫 묶음 규칙으로 다시 놓고, 무료 횟수는 쓰지 않는다")
    void emptyStoredBatchIsPlacedAgainForFree() {
        NovelHomeFeed feed = storedFeed(List.of(), 1, TODAY);
        holding(3L);
        when(novels.feed(NovelKind.RELAY, null, PageRequest.of(0, 8)))
                .thenReturn(new PageImpl<>(List.of(novel(4L), novel(3L), novel(2L))));

        NovelHomeFeedView view = service.current(ME);

        assertThat(ids(view)).containsExactly(4L, 2L);
        assertThat(feed.getNovelIds()).containsExactly(4L, 2L);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
        verify(feeds).lockByUserId(ME);
        verify(feeds, never()).insertIfAbsent(any(), any());
    }

    @Test @DisplayName("처음 열 때 놓을 작품이 없어 빈 묶음이 됐어도, 작품이 생긴 뒤 다시 열면 채운다")
    void emptyFirstBatchFillsOnceNovelsAppear() {
        NovelHomeFeed feed = storedFeed(List.of(), 0, TODAY);
        when(novels.feed(NovelKind.RELAY, null, PageRequest.of(0, 7)))
                .thenReturn(new PageImpl<>(List.of()), new PageImpl<>(List.of(novel(2L), novel(1L))));

        assertThat(ids(service.current(ME))).isEmpty();
        assertThat(ids(service.current(ME))).containsExactly(2L, 1L);
        assertThat(feed.getNovelIds()).containsExactly(2L, 1L);
        assertThat(feed.quota(2).leftOn(TODAY)).isEqualTo(2);
    }

    @Test @DisplayName("저장된 순서대로 보여 주되 보여 줄 수 없게 된 작품은 빼고, 빈자리는 새로 채우지 않는다")
    void currentSkipsUnshowableWithoutRefilling() {
        storedFeed(List.of(7L, 3L, 5L, 1L), 0, TODAY);
        unshowable.add(5L);

        assertThat(ids(service.current(ME))).containsExactly(7L, 3L, 1L);
        verify(novels, never()).pickForHomeFeed(any(), any(), anyInt());
        verify(novels, never()).feed(any(), any(), any());
        verify(feeds, never()).insertIfAbsent(any(), any());
    }

    @Test @DisplayName("열매 밭 작품에도 피드처럼 좋아요 수와 내가 누른 작품이 실린다")
    void currentCarriesLikesLikeFeed() {
        storedFeed(List.of(7L, 3L), 0, TODAY);
        when(likes.countPerNovel(anyCollection())).thenReturn(List.of(likeCount(7L, 120L)));
        when(likes.findAllByUserIdAndNovelIdIn(eq(ME), anyCollection()))
                .thenReturn(List.of(NovelLike.builder().userId(ME).novelId(3L).build()));

        List<NovelSummary> shown = service.current(ME).novels();

        assertThat(shown.get(0).likeCount()).isEqualTo(120L);
        assertThat(shown.get(0).liked()).isFalse();
        assertThat(shown.get(1).likeCount()).isZero();
        assertThat(shown.get(1).liked()).isTrue();
    }

    @Test @DisplayName("새로고침은 무료 횟수 하나를 쓰고, 지금 묶음·받은 열매·내 차례 작품을 뺀 새 작품으로 바꾼다")
    void refreshReplacesWithNewNovels() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), 0, TODAY);
        holding(20L);
        myTurns(21L);
        List<Long> fresh = List.of(31L, 32L, 33L, 34L, 35L, 36L, 37L);
        when(novels.pickForHomeFeed(ME, Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 20L, 21L), 7)).thenReturn(fresh);

        NovelHomeFeedView view = service.refresh(ME);

        assertThat(ids(view)).isEqualTo(fresh);
        assertThat(feed.getNovelIds()).isEqualTo(fresh);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
    }

    @Test @DisplayName("하루 두 번을 다 쓰면 세 번째 새로고침은 429로 막고 묶음을 그대로 둔다")
    void thirdRefreshIsRejected() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 0, TODAY);
        when(novels.pickForHomeFeed(eq(ME), any(), eq(7))).thenReturn(List.of(11L), List.of(12L));
        service.refresh(ME);
        service.refresh(ME);
        List<Long> afterSecond = feed.getNovelIds();

        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT);
        assertThat(ErrorCode.NOVEL_REFRESH_DAILY_LIMIT.getStatus().value()).isEqualTo(429);
        assertThat(feed.getNovelIds()).isEqualTo(afterSecond);
        assertThat(feed.quota(2).leftOn(TODAY)).isZero();
        verify(novels, times(2)).pickForHomeFeed(eq(ME), any(), eq(7));
    }

    @Test @DisplayName("새로 놓을 작품이 없으면 무료 횟수를 쓰지 않고 409로 알린다")
    void refreshWithoutNewNovelsKeepsQuota() {
        NovelHomeFeed feed = storedFeed(List.of(1L, 2L), 1, TODAY);
        when(novels.pickForHomeFeed(eq(ME), any(), eq(7))).thenReturn(List.of());

        assertThatThrownBy(() -> service.refresh(ME))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_FEED_NO_NEW);
        assertThat(feed.quota(2).leftOn(TODAY)).isEqualTo(1);
        assertThat(feed.getNovelIds()).containsExactly(1L, 2L);
    }

    @Test @DisplayName("새 작품이 모자라면 남는 자리를 이전 묶음에서 채운다 — 보여 줄 수 없거나 내 차례가 된 작품은 빼고")
    void shortRefreshKeepsSomeOfPrevious() {
        storedFeed(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), 0, TODAY);
        unshowable.add(2L);
        myTurns(3L);
        when(novels.pickForHomeFeed(eq(ME), any(), eq(7))).thenReturn(List.of(40L, 41L));

        List<Long> shown = ids(service.refresh(ME));

        assertThat(shown).hasSize(7).startsWith(40L, 41L);
        assertThat(shown.subList(2, 7)).containsExactlyInAnyOrder(1L, 4L, 5L, 6L, 7L);
    }

    @Test @DisplayName("KST 자정이 지나면 무료 횟수가 다시 찬다")
    void quotaRefillsAfterKstMidnight() {
        NovelHomeFeed feed = storedFeed(List.of(1L), 2, TODAY);
        assertThat(service.current(ME).freeRefreshesLeftToday()).isZero();

        when(clock.instant()).thenReturn(NEXT_MIDNIGHT.plusSeconds(60));
        when(novels.pickForHomeFeed(eq(ME), any(), eq(7))).thenReturn(List.of(11L));
        assertThat(service.current(ME).freeRefreshesLeftToday()).isEqualTo(2);
        NovelHomeFeedView refreshed = service.refresh(ME);

        assertThat(refreshed.freeRefreshesLeftToday()).isEqualTo(1);
        assertThat(refreshed.resetsAt()).isEqualTo(Instant.parse("2026-10-16T15:00:00Z"));
        assertThat(feed.getFreeResetDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test @DisplayName("묶음이 아직 없을 때 새로고침하면 빈 묶음을 만들어 잠근 뒤 센다")
    void refreshCreatesMissingRow() {
        NovelHomeFeed created = new NovelHomeFeed(ME, TODAY);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.empty(), Optional.of(created));
        when(novels.pickForHomeFeed(eq(ME), any(), eq(7))).thenReturn(List.of(11L, 12L));

        NovelHomeFeedView view = service.refresh(ME);

        verify(feeds).insertIfAbsent(ME, TODAY);
        assertThat(ids(view)).containsExactly(11L, 12L);
        assertThat(view.freeRefreshesLeftToday()).isEqualTo(1);
    }

    private NovelHomeFeed storedFeed(List<Long> ids, int usedToday, LocalDate countedOn) {
        NovelHomeFeed feed = new NovelHomeFeed(ME, countedOn);
        feed.placeFirst(ids);
        ReflectionTestUtils.setField(feed, "freeUsedToday", (short) usedToday);
        when(feeds.lockByUserId(ME)).thenReturn(Optional.of(feed));
        return feed;
    }
    private void holding(Long... novelIds) {
        List<NovelDelivery> held = Arrays.stream(novelIds)
                .map(id -> new NovelDelivery(id, 100L + id, ME, NOON.plus(Duration.ofHours(1)))).toList();
        when(deliveries.holding(ME, NOON, NovelService.HOME_LIST_PAGE)).thenReturn(held);
    }
    private void myTurns(Long... novelIds) {
        when(novels.myTurns(ME, NOON, NovelService.HOME_LIST_PAGE)).thenReturn(Arrays.stream(novelIds).map(this::novel).toList());
    }
    private Novel novel(long id) {
        Novel novel = new Novel(2L, NovelKind.RELAY, "작품 " + id, "소개", "판타지", 2, 10, 24, true);
        ReflectionTestUtils.setField(novel, "id", id);
        ReflectionTestUtils.setField(novel, "createdAt", NOON);
        return novel;
    }
    private static List<Long> ids(NovelHomeFeedView view) {
        return view.novels().stream().map(NovelSummary::id).toList();
    }
    private static NovelLikeRepository.NovelLikeCount likeCount(Long novelId, long count) {
        return new NovelLikeRepository.NovelLikeCount() {
            public Long getNovelId() { return novelId; }
            public long getLikeCount() { return count; }
        };
    }
}
