package app.bookey.api.novel;

import app.bookey.domain.novel.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelSunlightService sunlight = mock(NovelSunlightService.class);
    private final NovelDeliveryService service = new NovelDeliveryService(deliveries, novels, chapters, sunlight);
    private Novel open;

    @BeforeEach void setup() {
        open = Novel.open(1L, "열매", "소개", "판타지", 3, 24, 15, null);
        ReflectionTestUtils.setField(open, "id", 10L);
    }
    private NovelChapter chapter(long id, Long author, int depth) {
        NovelChapter c = new NovelChapter(10L, author, null, depth, "회차", "본문");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
    @SuppressWarnings("unchecked")
    private List<NovelDelivery> savedDeliveries() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(deliveries).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test @DisplayName("새 회차는 회차 작가와 만든 사람을 빼고 작품 장르로 10명에게 배달한다")
    void deliversToPickedRecipients() {
        when(deliveries.pickRecipients(eq(10L), eq(100L), eq("판타지"), any(), eq(10))).thenReturn(List.of(5L, 6L));
        int sent = service.deliverNewChapter(open, chapter(100L, 3L, 1), NOW);
        assertThat(sent).isEqualTo(2);
        verify(deliveries).pickRecipients(eq(10L), eq(100L), eq("판타지"), eq(Set.of(1L, 3L)), eq(10));
        List<NovelDelivery> saved = savedDeliveries();
        assertThat(saved).extracting(NovelDelivery::getRecipientId).containsExactly(5L, 6L);
        assertThat(saved).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(NovelDeliveryStatus.DELIVERED);
            assertThat(d.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            assertThat(d.getChapterId()).isEqualTo(100L);
        });
    }
    @Test @DisplayName("목표 회차에 닿은 가지는 더 배달하지 않는다")
    void stopsAtTargetDepth() {
        assertThat(service.deliverNewChapter(open, chapter(100L, 3L, 3), NOW)).isZero();
        verifyNoInteractions(deliveries);
    }
    @Test @DisplayName("한 줄기로 쓰는 작품과 완결한 작품은 배달하지 않는다")
    void onlyOngoingOpenNovelsDeliver() {
        Novel circle = new Novel(1L, NovelKind.RELAY, "제목", "소개", "일상", 3, 10, 24, true);
        assertThat(service.deliverNewChapter(circle, chapter(100L, 3L, 1), NOW)).isZero();
        open.complete();
        assertThat(service.deliverNewChapter(open, chapter(100L, 3L, 1), NOW)).isZero();
        verifyNoInteractions(deliveries);
    }
    @Test @DisplayName("마감이 지난 열매는 거두고 다른 한 사람에게 다시 보낸다")
    void overdueDeliveryIsResent() {
        NovelDelivery overdue = new NovelDelivery(10L, 100L, 5L, Instant.now().minusSeconds(1));
        when(deliveries.findById(7L)).thenReturn(Optional.of(overdue));
        when(deliveries.lockById(7L)).thenReturn(Optional.of(overdue));
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        when(chapters.findById(100L)).thenReturn(Optional.of(chapter(100L, 3L, 1)));
        when(deliveries.pickRecipients(eq(10L), eq(100L), any(), any(), eq(1))).thenReturn(List.of(8L));
        service.expireOverdue(7L);
        assertThat(overdue.getStatus()).isEqualTo(NovelDeliveryStatus.EXPIRED);
        assertThat(savedDeliveries()).extracting(NovelDelivery::getRecipientId).containsExactly(8L);
    }
    @Test @DisplayName("아직 마감 전인 열매는 배치가 건드리지 않는다")
    void deliveryBeforeDeadlineIsKept() {
        NovelDelivery held = new NovelDelivery(10L, 100L, 5L, Instant.now().plusSeconds(60));
        when(deliveries.findById(7L)).thenReturn(Optional.of(held));
        when(deliveries.lockById(7L)).thenReturn(Optional.of(held));
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        service.expireOverdue(7L);
        assertThat(held.getStatus()).isEqualTo(NovelDeliveryStatus.DELIVERED);
        verify(deliveries, never()).saveAll(any());
    }
    @Test @DisplayName("완결하면 들고 있는 열매를 모두 거둔다")
    void closeAllExpiresHeldDeliveries() {
        NovelDelivery first = new NovelDelivery(10L, 100L, 5L, NOW);
        NovelDelivery second = new NovelDelivery(10L, 101L, 6L, NOW);
        when(deliveries.findByNovelIdAndStatus(10L, NovelDeliveryStatus.DELIVERED)).thenReturn(List.of(first, second));
        service.closeAll(open);
        assertThat(List.of(first, second)).extracting(NovelDelivery::getStatus).containsOnly(NovelDeliveryStatus.EXPIRED);
        verify(deliveries, never()).saveAll(any());
    }
    @Test @DisplayName("가지 자리는 나무·수거함의 가지와 들고 있는 열매를 뺀 만큼이다")
    void freeSlotsCountGrowingBranchesAndHeldFruit() {
        when(chapters.countGrowingChildren(100L)).thenReturn(9L);
        when(deliveries.countByChapterIdAndStatus(100L, NovelDeliveryStatus.DELIVERED)).thenReturn(4L);
        assertThat(service.freeSlots(open, chapter(100L, 3L, 1))).isEqualTo(2);
        when(deliveries.pickRecipients(any(), any(), any(), any(), eq(2))).thenReturn(List.of(5L, 6L));
        assertThat(service.deliverNewChapter(open, chapter(100L, 3L, 1), NOW)).isEqualTo(2);
    }
    @Test @DisplayName("가지 자리가 다 찼으면 넘긴 열매를 다시 보내지 않는다")
    void fullChapterIsNotResent() {
        when(chapters.countGrowingChildren(100L)).thenReturn(11L);
        when(deliveries.countByChapterIdAndStatus(100L, NovelDeliveryStatus.DELIVERED)).thenReturn(4L);
        assertThat(service.resend(open, chapter(100L, 3L, 1), NOW)).isZero();
        verify(deliveries, never()).pickRecipients(any(), any(), any(), any(), anyInt());
    }
    @Test @DisplayName("수거함에 들어간 회차는 배달하지 않는다")
    void prunedChapterIsNotDelivered() {
        NovelChapter pruned = chapter(100L, 3L, 2);
        pruned.prune(NOW);
        assertThat(service.resend(open, pruned, NOW)).isZero();
        verify(deliveries, never()).pickRecipients(any(), any(), any(), any(), anyInt());
    }
    @Test @DisplayName("내 글 잇기는 햇살을 내고 내 회차를 나에게 일주일 동안 보낸다")
    void sendToSelfSpendsSunlight() {
        when(sunlight.selfBranchCost()).thenReturn(5);
        when(deliveries.holdingIn(10L, 3L, NOW)).thenReturn(Optional.empty());
        when(deliveries.saveAndFlush(any(NovelDelivery.class))).thenAnswer(call -> {
            NovelDelivery saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 70L);
            return saved;
        });
        NovelDelivery d = service.sendToSelf(open, chapter(100L, 3L, 1), 3L, NOW);
        assertThat(d.isSelf()).isTrue();
        assertThat(d.getRecipientId()).isEqualTo(3L);
        assertThat(d.getSunlightPaid()).isEqualTo(5);
        assertThat(d.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        verify(sunlight).spend(3L, 5, NovelSunlightTransactionKind.SELF_BRANCH, "NOVEL_DELIVERY", 70L);
    }
    @Test @DisplayName("남이 쓴 회차나 자리가 다 찬 회차에는 내 글 잇기를 할 수 없다")
    void sendToSelfRequiresAuthorAndFreeSlot() {
        assertThatThrownBy(() -> service.sendToSelf(open, chapter(100L, 3L, 1), 4L, NOW))
                .isInstanceOf(app.bookey.common.error.ApiException.class);
        when(chapters.countGrowingChildren(100L)).thenReturn(15L);
        assertThatThrownBy(() -> service.sendToSelf(open, chapter(100L, 3L, 1), 3L, NOW))
                .isInstanceOf(app.bookey.common.error.ApiException.class)
                .extracting("errorCode").isEqualTo(app.bookey.common.error.ErrorCode.NOVEL_BRANCH_FULL);
        verify(sunlight, never()).spend(any(), anyInt(), any(), any(), any());
    }
    @Test @DisplayName("내 글 잇기를 쓰지 않고 끝내면 햇살을 돌려주고 다른 사람에게 보내지 않는다")
    void unwrittenSelfDeliveryRefunds() {
        NovelDelivery self = NovelDelivery.toSelf(10L, 100L, 3L, NOW, 5);
        ReflectionTestUtils.setField(self, "id", 70L);
        service.settleUnwritten(open, self, NOW, true);
        verify(sunlight).grant(3L, 5, NovelSunlightTransactionKind.REFUND, "NOVEL_DELIVERY", 70L);
        verify(deliveries, never()).pickRecipients(any(), any(), any(), any(), anyInt());
    }
}
