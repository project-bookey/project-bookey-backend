package app.bookey.api.novel;

import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/**
 * 가지 자리 — 열매로 퍼뜨리는 작품(OPEN)의 회차 하나에는 작품의 가지 상한만큼 가지가 붙는다.
 * 나무에 있는 가지·수거함의 가지·자리를 맡은 사람이 자리를 차지하고, 첫 작가의 줄기는 따로 센다.
 * 무작위 배달은 없앴다(2026-10-21) — 홈에 열매로 놓인 회차를 고른 사람이 자리를 맡고(CLAIM), 쓰지 않고 끝나면 자리가 다시 빈다.
 * 부르는 쪽이 작품을 잠근(lockById) 트랜잭션 안에서 불러, 같은 회차의 자리가 겹치지 않게 한다.
 */
@Service @RequiredArgsConstructor
public class NovelDeliveryService {
    /** 내 글 잇기는 자리를 맡을 때보다 넉넉하게 일주일을 준다. */
    static final Duration SELF_DELIVERY_TIME = Duration.ofDays(7);
    private final NovelDeliveryRepository deliveries;
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;
    private final NovelSunlightService sunlight;

    /** 이 회차에 남은 가지 자리. */
    public int freeSlots(Novel n, NovelChapter c) {
        long taken = chapters.countBranchChildren(c.getId())
                + deliveries.countByChapterIdAndStatus(c.getId(), NovelDeliveryStatus.DELIVERED);
        return (int) Math.max(0, n.getBranchLimit() - taken);
    }

    /**
     * 내가 이 회차에서 자리를 맡고 이어 쓸 수 있는가. 앱은 이 값으로만 이어 쓰기 버튼을 보인다.
     * 홈 열매를 고르는 조건(NovelChapterRepository.CONTINUABLE_FOR_USER)과 같게 둔다.
     */
    public boolean canClaim(Novel n, NovelChapter c, Long me, Instant now) {
        return canGrow(n, c) && !n.isOwner(me) && !c.isWrittenBy(me) && c.getAuthorId() != null
                && deliveries.holdingIn(n.getId(), me, now).isEmpty() && !chapters.hasBranchBy(c.getId(), me)
                && freeSlots(n, c) > 0;
    }

    /**
     * 자리 맡기 — 홈의 열매를 골라 쓰기 시작하면 작품의 제한 시간 동안 그 회차의 자리를 하나 맡는다.
     * 내 작품은 줄기로, 내가 쓴 회차는 햇살로 잇는다. 한 작품에서는 자리를 하나만 맡는다.
     */
    public NovelDelivery claim(Novel n, NovelChapter c, Long me, Instant now) {
        if (!canGrow(n, c)) throw ApiException.of(ErrorCode.NOVEL_CLOSED);
        if (n.isOwner(me) || c.isWrittenBy(me)) throw ApiException.of(ErrorCode.NOVEL_CLAIM_NOT_ALLOWED);
        if (c.getAuthorId() == null) throw ApiException.of(ErrorCode.NOT_FOUND);
        if (deliveries.holdingIn(n.getId(), me, now).isPresent()) throw ApiException.of(ErrorCode.NOVEL_BRANCH_HOLDING);
        if (chapters.hasBranchBy(c.getId(), me)) throw ApiException.of(ErrorCode.NOVEL_ALREADY_BRANCHED);
        if (freeSlots(n, c) <= 0) throw ApiException.of(ErrorCode.NOVEL_BRANCH_FULL);
        Instant dueAt = now.plus(Duration.ofHours(n.getTurnHours()));
        return deliveries.saveAndFlush(NovelDelivery.claim(n.getId(), c.getId(), me, dueAt));
    }

    /** 내가 쓴 회차라 햇살을 내고 다음 회차를 이어 쓸 수 있는가. 첫 작가의 줄기 회차는 줄기로 잇는다. */
    public boolean canSendToSelf(Novel n, NovelChapter c, Long me, Instant now) {
        return canGrow(n, c) && c.isWrittenBy(me) && !c.isTrunk()
                && deliveries.holdingIn(n.getId(), me, now).isEmpty() && freeSlots(n, c) > 0;
    }

    /** 내 글 잇기 — 햇살을 내고 내 회차를 나에게 배달한다. 그 뒤로는 자리를 맡은 열매처럼 쓰고 발행한다. */
    public NovelDelivery sendToSelf(Novel n, NovelChapter c, Long me, Instant now) {
        if (!n.isOpen() || !c.isActive() || !c.isWrittenBy(me) || c.isTrunk()) throw ApiException.of(ErrorCode.FORBIDDEN);
        if (!n.canGrowFrom(c.getChapterNumber())) throw ApiException.of(ErrorCode.NOVEL_CLOSED);
        if (deliveries.holdingIn(n.getId(), me, now).isPresent()) throw ApiException.of(ErrorCode.NOVEL_BRANCH_HOLDING);
        if (freeSlots(n, c) <= 0) throw ApiException.of(ErrorCode.NOVEL_BRANCH_FULL);
        int cost = sunlight.selfBranchCost();
        NovelDelivery d = deliveries.saveAndFlush(NovelDelivery.toSelf(n.getId(), c.getId(), me, now.plus(SELF_DELIVERY_TIME), cost));
        sunlight.spend(me, cost, NovelSunlightTransactionKind.SELF_BRANCH, "NOVEL_DELIVERY", d.getId());
        return d;
    }

    /** 쓰지 않고 끝난 열매를 마무리한다. 자리는 저절로 빈다. 내 글 잇기였으면 낸 햇살을 돌려준다. */
    public void settleUnwritten(NovelDelivery d) {
        if (!d.isSelf()) return;
        sunlight.grant(d.getRecipientId(), d.getSunlightPaid(), NovelSunlightTransactionKind.REFUND, "NOVEL_DELIVERY", d.getId());
    }

    /** 작품이 완결되면 아직 맡은 자리를 모두 거둔다. 내 글 잇기의 햇살은 돌려준다. */
    public void closeAll(Novel n) {
        for (NovelDelivery d : deliveries.findByNovelIdAndStatus(n.getId(), NovelDeliveryStatus.DELIVERED)) {
            d.expire();
            settleUnwritten(d);
        }
    }

    /** 가지가 수거되면 그 회차에 맡은 자리를 거둔다. */
    public void closeChapter(NovelChapter c) {
        for (NovelDelivery d : deliveries.findByChapterIdAndStatus(c.getId(), NovelDeliveryStatus.DELIVERED)) {
            d.expire();
            settleUnwritten(d);
        }
    }

    /** 배치가 마감이 지난 자리를 거둔다. 자리가 비면 그 회차가 다시 홈의 열매 후보가 된다. */
    @Transactional
    public void expireOverdue(Long deliveryId) {
        Instant now = Instant.now();
        NovelDelivery found = deliveries.findById(deliveryId).orElse(null);
        if (found == null) return;
        // 작품 → 열매 순서로 잠가 가지 발행·내려놓기와 같은 순서를 지킨다.
        Novel n = novels.lockById(found.getNovelId()).orElse(null);
        NovelDelivery d = deliveries.lockById(deliveryId).orElse(null);
        if (n == null || d == null || !d.isOverdue(now)) return;
        d.expire();
        settleUnwritten(d);
    }

    /** 연재 중인 OPEN 작품의, 나무에 있고 목표 회차에 닿지 않은 회차에만 가지를 뻗는다. */
    private static boolean canGrow(Novel n, NovelChapter c) {
        return n.isOpen() && c.isActive() && n.canGrowFrom(c.getChapterNumber());
    }
}
