package app.bookey.api.novel;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final Long OWNER = 1L;
    private static final Long AUTHOR = 3L;
    private static final Long READER = 5L;
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelSunlightService sunlight = mock(NovelSunlightService.class);
    private final NovelDeliveryService service = new NovelDeliveryService(deliveries, novels, chapters, sunlight);
    private Novel open;

    @BeforeEach void setup() {
        // 목표 회차 3, 회차마다 가지 3개, 자리를 맡으면 24시간.
        open = Novel.open(OWNER, "열매", "소개", "판타지", 3, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 10L);
        when(deliveries.holdingIn(eq(10L), any(), any())).thenReturn(Optional.empty());
        when(deliveries.saveAndFlush(any(NovelDelivery.class))).thenAnswer(call -> {
            NovelDelivery saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 70L);
            return saved;
        });
    }
    private NovelChapter chapter(long id, Long author, int depth) {
        NovelChapter c = new NovelChapter(10L, author, depth == 1 ? null : 99L, depth, "회차", "본문");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
    private void taken(long chapterId, long branches, long claims) {
        when(chapters.countBranchChildren(chapterId)).thenReturn(branches);
        when(deliveries.countByChapterIdAndStatus(chapterId, NovelDeliveryStatus.DELIVERED)).thenReturn(claims);
    }

    @Test @DisplayName("가지 자리는 나무·수거함의 가지와 자리를 맡은 사람을 뺀 만큼이다(첫 작가의 줄기는 세지 않는다)")
    void freeSlotsCountBranchesAndClaims() {
        taken(100L, 1, 1);
        assertThat(service.freeSlots(open, chapter(100L, AUTHOR, 1))).isEqualTo(1);
        taken(100L, 2, 2);
        assertThat(service.freeSlots(open, chapter(100L, AUTHOR, 1))).isZero();
    }

    @Test @DisplayName("열매를 골라 쓰기 시작하면 작품의 제한 시간 동안 그 회차의 자리를 맡는다")
    void claimHoldsASlotUntilTheTimeLimit() {
        NovelDelivery claimed = service.claim(open, chapter(100L, AUTHOR, 1), READER, NOW);
        assertThat(claimed.getSource()).isEqualTo(NovelDeliverySource.CLAIM);
        assertThat(claimed.getRecipientId()).isEqualTo(READER);
        assertThat(claimed.getChapterId()).isEqualTo(100L);
        assertThat(claimed.getStatus()).isEqualTo(NovelDeliveryStatus.DELIVERED);
        assertThat(claimed.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(service.canClaim(open, chapter(101L, AUTHOR, 1), READER, NOW)).isTrue();
    }

    @Test @DisplayName("자리가 다 찼거나 이 작품에서 이미 자리를 맡았으면 자리를 맡지 못한다")
    void claimNeedsAFreeSlotAndNoOtherHolding() {
        taken(100L, 2, 1);
        assertThatThrownBy(() -> service.claim(open, chapter(100L, AUTHOR, 1), READER, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_BRANCH_FULL);
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 1), READER, NOW)).isFalse();

        NovelDelivery other = NovelDelivery.claim(10L, 200L, READER, NOW.plusSeconds(60));
        when(deliveries.holdingIn(10L, READER, NOW)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.claim(open, chapter(101L, AUTHOR, 1), READER, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_BRANCH_HOLDING);
        verify(deliveries, never()).saveAndFlush(any());
    }

    @Test @DisplayName("이미 가지를 뻗은 회차에는 다시 자리를 맡지 않는다 — 한 사람은 한 회차에 가지 하나")
    void oneBranchPerReaderPerChapter() {
        when(chapters.hasBranchBy(100L, READER)).thenReturn(true);
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 1), READER, NOW)).isFalse();
        assertThatThrownBy(() -> service.claim(open, chapter(100L, AUTHOR, 1), READER, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_ALREADY_BRANCHED);
        verify(deliveries, never()).saveAndFlush(any());
    }

    @Test @DisplayName("내가 쓴 회차와 내 작품의 회차에는 자리를 맡지 않는다 — 내 회차는 햇살로, 내 작품은 줄기로 잇는다")
    void ownChaptersAndOwnNovelAreNotClaimed() {
        assertThatThrownBy(() -> service.claim(open, chapter(100L, AUTHOR, 2), AUTHOR, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_CLAIM_NOT_ALLOWED);
        assertThatThrownBy(() -> service.claim(open, chapter(100L, AUTHOR, 2), OWNER, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_CLAIM_NOT_ALLOWED);
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 2), AUTHOR, NOW)).isFalse();
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 2), OWNER, NOW)).isFalse();
    }

    @Test @DisplayName("목표 회차에 닿은 회차·완결한 작품·한 줄기 작품에는 가지를 뻗지 않는다")
    void onlyGrowingChaptersOfOngoingOpenNovels() {
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 3), READER, NOW)).isFalse();
        assertThatThrownBy(() -> service.claim(open, chapter(100L, AUTHOR, 3), READER, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_CLOSED);
        Novel circle = new Novel(OWNER, NovelKind.RELAY, "제목", "소개", "일상", 3, 10, 24, true);
        assertThat(service.canClaim(circle, chapter(100L, AUTHOR, 1), READER, NOW)).isFalse();
        open.complete();
        assertThat(service.canClaim(open, chapter(100L, AUTHOR, 1), READER, NOW)).isFalse();
    }

    @Test @DisplayName("마감이 지난 자리는 거두기만 한다 — 다른 사람에게 다시 보내지 않고 자리가 빈다")
    void overdueClaimIsReleased() {
        NovelDelivery overdue = NovelDelivery.claim(10L, 100L, READER, Instant.now().minusSeconds(1));
        when(deliveries.findById(7L)).thenReturn(Optional.of(overdue));
        when(deliveries.lockById(7L)).thenReturn(Optional.of(overdue));
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        service.expireOverdue(7L);
        assertThat(overdue.getStatus()).isEqualTo(NovelDeliveryStatus.EXPIRED);
        verify(deliveries, never()).saveAll(any());
        verify(deliveries, never()).saveAndFlush(any());
        verifyNoInteractions(sunlight);
    }

    @Test @DisplayName("아직 마감 전인 자리는 배치가 건드리지 않는다")
    void claimBeforeDeadlineIsKept() {
        NovelDelivery held = NovelDelivery.claim(10L, 100L, READER, Instant.now().plusSeconds(60));
        when(deliveries.findById(7L)).thenReturn(Optional.of(held));
        when(deliveries.lockById(7L)).thenReturn(Optional.of(held));
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        service.expireOverdue(7L);
        assertThat(held.getStatus()).isEqualTo(NovelDeliveryStatus.DELIVERED);
    }

    @Test @DisplayName("완결하면 맡은 자리를 모두 거두고 내 글 잇기의 햇살은 돌려준다")
    void closeAllExpiresHeldClaimsAndRefundsSelf() {
        NovelDelivery claimed = NovelDelivery.claim(10L, 100L, READER, NOW);
        NovelDelivery self = NovelDelivery.toSelf(10L, 101L, AUTHOR, NOW, 5);
        ReflectionTestUtils.setField(self, "id", 71L);
        when(deliveries.findByNovelIdAndStatus(10L, NovelDeliveryStatus.DELIVERED)).thenReturn(List.of(claimed, self));
        service.closeAll(open);
        assertThat(List.of(claimed, self)).extracting(NovelDelivery::getStatus).containsOnly(NovelDeliveryStatus.EXPIRED);
        verify(sunlight).grant(AUTHOR, 5, NovelSunlightTransactionKind.REFUND, "NOVEL_DELIVERY", 71L);
        verifyNoMoreInteractions(sunlight);
    }

    @Test @DisplayName("내 글 잇기는 햇살을 내고 내 회차를 나에게 일주일 동안 보낸다")
    void sendToSelfSpendsSunlight() {
        when(sunlight.selfBranchCost()).thenReturn(5);
        NovelDelivery d = service.sendToSelf(open, chapter(100L, AUTHOR, 2), AUTHOR, NOW);
        assertThat(d.isSelf()).isTrue();
        assertThat(d.getRecipientId()).isEqualTo(AUTHOR);
        assertThat(d.getSunlightPaid()).isEqualTo(5);
        assertThat(d.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        verify(sunlight).spend(AUTHOR, 5, NovelSunlightTransactionKind.SELF_BRANCH, "NOVEL_DELIVERY", 70L);
    }

    @Test @DisplayName("남이 쓴 회차·자리가 다 찬 회차·첫 작가의 줄기 회차에는 햇살로 이어 쓰지 않는다")
    void sendToSelfRequiresOwnNonTrunkChapterWithFreeSlot() {
        assertThatThrownBy(() -> service.sendToSelf(open, chapter(100L, AUTHOR, 2), READER, NOW))
                .isInstanceOf(ApiException.class);
        NovelChapter trunk = NovelChapter.trunk(10L, OWNER, null, "뿌리", "본문");
        ReflectionTestUtils.setField(trunk, "id", 90L);
        assertThat(service.canSendToSelf(open, trunk, OWNER, NOW)).isFalse();
        assertThatThrownBy(() -> service.sendToSelf(open, trunk, OWNER, NOW)).isInstanceOf(ApiException.class);
        taken(100L, 3, 0);
        assertThatThrownBy(() -> service.sendToSelf(open, chapter(100L, AUTHOR, 2), AUTHOR, NOW))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_BRANCH_FULL);
        verify(sunlight, never()).spend(any(), anyInt(), any(), any(), any());
    }

    @Test @DisplayName("쓰지 않고 끝난 내 글 잇기는 햇살을 돌려주고, 자리를 맡은 열매는 그냥 자리만 빈다")
    void unwrittenSelfDeliveryRefunds() {
        NovelDelivery self = NovelDelivery.toSelf(10L, 100L, AUTHOR, NOW, 5);
        ReflectionTestUtils.setField(self, "id", 70L);
        service.settleUnwritten(self);
        verify(sunlight).grant(AUTHOR, 5, NovelSunlightTransactionKind.REFUND, "NOVEL_DELIVERY", 70L);
        service.settleUnwritten(NovelDelivery.claim(10L, 100L, READER, NOW));
        verifyNoMoreInteractions(sunlight);
    }
}
