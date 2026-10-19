package app.bookey.api.novel;

import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelBranchBinServiceTest {
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelDeliveryService delivery = mock(NovelDeliveryService.class);
    private final NovelSunlightService sunlight = mock(NovelSunlightService.class);
    private final NovelBranchBinService service = new NovelBranchBinService(novels, chapters, delivery, sunlight);
    private Novel open;
    private NovelChapter parent;
    private NovelChapter tip;

    @BeforeEach void setup() {
        open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 10, null);
        ReflectionTestUtils.setField(open, "id", 10L);
        ReflectionTestUtils.setField(open, "chapterCount", 2);
        parent = new NovelChapter(10L, 1L, null, 1, "뿌리", "본문");
        ReflectionTestUtils.setField(parent, "id", 100L);
        tip = new NovelChapter(10L, 5L, 100L, 2, "가지", "본문");
        ReflectionTestUtils.setField(tip, "id", 200L);
        ReflectionTestUtils.setField(tip, "judgedFrom", Instant.now().minus(Duration.ofDays(31)));
        when(chapters.novelIdOf(200L)).thenReturn(Optional.of(10L));
        when(chapters.findById(200L)).thenReturn(Optional.of(tip));
        when(chapters.findById(100L)).thenReturn(Optional.of(parent));
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        when(chapters.isTip(200L)).thenReturn(true);
        when(sunlight.restoreCost()).thenReturn(3);
    }

    @Test @DisplayName("한 달이 지나도 조회가 50회를 못 넘긴 끝 가지는 수거함으로 가고 일주일 뒤 지울 날이 정해진다")
    void staleTipIsPruned() {
        service.pruneIfStale(200L);
        assertThat(tip.isPruned()).isTrue();
        assertThat(Duration.between(tip.getPrunedAt(), tip.getPurgeAt())).isEqualTo(Duration.ofDays(7));
        assertThat(open.getChapterCount()).isEqualTo(1);
        verify(delivery).closeChapter(eq(open), eq(tip), any());
    }
    @Test @DisplayName("조회가 50회를 넘었거나 아직 한 달이 안 됐거나 이어 쓴 가지가 있으면 수거하지 않는다")
    void livelyOrGrowingBranchStays() {
        ReflectionTestUtils.setField(tip, "viewCount", 50);
        service.pruneIfStale(200L);
        ReflectionTestUtils.setField(tip, "viewCount", 0);
        ReflectionTestUtils.setField(tip, "judgedFrom", Instant.now().minus(Duration.ofDays(29)));
        service.pruneIfStale(200L);
        ReflectionTestUtils.setField(tip, "judgedFrom", Instant.now().minus(Duration.ofDays(31)));
        when(chapters.isTip(200L)).thenReturn(false);
        service.pruneIfStale(200L);
        assertThat(tip.isActive()).isTrue();
        verify(delivery, never()).closeChapter(any(), any(), any());
    }
    @Test @DisplayName("완결한 작품의 가지와 뿌리 회차는 수거하지 않는다")
    void completedNovelAndRootStay() {
        open.complete();
        service.pruneIfStale(200L);
        assertThat(tip.isActive()).isTrue();
        assertThat(parent.isStale(Instant.now().plus(Duration.ofDays(400)))).isFalse();
    }
    @Test @DisplayName("살리면 햇살을 내고 나무로 돌아가며, 살린 날부터 다시 한 달을 센다")
    void restoreSpendsSunlightAndRestartsJudging() {
        tip.prune(Instant.now());
        open.hideBranch();
        when(chapters.binOf(5L)).thenReturn(List.of());
        service.restore(5L, 200L);
        assertThat(tip.isActive()).isTrue();
        assertThat(tip.getPurgeAt()).isNull();
        assertThat(tip.getJudgedFrom()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
        assertThat(open.getChapterCount()).isEqualTo(2);
        verify(sunlight).spend(5L, 3, NovelSunlightTransactionKind.RESTORE_BRANCH, "NOVEL_CHAPTER", 200L);
    }
    @Test @DisplayName("햇살이 모자라면 살리지 못하고 수거함에 그대로 둔다")
    void restoreWithoutSunlightKeepsBranchInBin() {
        tip.prune(Instant.now());
        when(sunlight.spend(any(), anyInt(), any(), any(), any()))
                .thenThrow(ApiException.of(ErrorCode.NOVEL_SUNLIGHT_INSUFFICIENT));
        assertThatThrownBy(() -> service.restore(5L, 200L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_SUNLIGHT_INSUFFICIENT);
        assertThat(tip.isPruned()).isTrue();
    }
    @Test @DisplayName("남의 가지나 지울 날이 지난 가지는 살리거나 버릴 수 없다")
    void onlyMyLiveBinBranches() {
        tip.prune(Instant.now());
        assertThatThrownBy(() -> service.discard(6L, 200L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        ReflectionTestUtils.setField(tip, "purgeAt", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.restore(5L, 200L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_BIN_GONE);
        verify(sunlight, never()).spend(any(), anyInt(), any(), any(), any());
    }
    @Test @DisplayName("버리면 바로 지우고 부모 회차에 생긴 자리로 다시 배달한다")
    void discardFreesParentSlot() {
        tip.prune(Instant.now());
        when(chapters.binOf(5L)).thenReturn(List.of());
        service.discard(5L, 200L);
        assertThat(tip.getState()).isEqualTo(NovelChapterState.REMOVED);
        verify(delivery).resend(eq(open), eq(parent), any());
    }
    @Test @DisplayName("일주일 동안 고르지 않은 가지는 배치가 지우고 부모에 다시 배달한다")
    void purgeAfterGrace() {
        tip.prune(Instant.now().minus(Duration.ofDays(8)));
        service.purgeIfDue(200L);
        assertThat(tip.getState()).isEqualTo(NovelChapterState.REMOVED);
        verify(delivery).resend(eq(open), eq(parent), any());
    }
    @Test @DisplayName("지울 날 전인 가지는 배치가 건드리지 않는다")
    void purgeWaitsForGrace() {
        tip.prune(Instant.now());
        service.purgeIfDue(200L);
        assertThat(tip.isPruned()).isTrue();
        verify(delivery, never()).resend(any(), any(), any());
    }
}
