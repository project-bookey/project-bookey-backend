package app.bookey.api.novel;

import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/**
 * 열매 배달 — 열매로 퍼뜨리는 작품(OPEN)의 회차를 무작위로 고른 사람에게 보낸다.
 * 부르는 쪽이 작품을 잠근(lockById) 트랜잭션 안에서 불러, 같은 작품의 배달이 겹치지 않게 한다.
 * 회차 하나에 붙는 가지는 작품의 가지 상한까지다. 나무에 있는 가지·수거함의 가지·들고 있는 열매가 자리를 차지한다.
 */
@Service @RequiredArgsConstructor
public class NovelDeliveryService {
    /** 내 글 잇기는 무작위 열매보다 넉넉하게 일주일을 준다. */
    static final Duration SELF_DELIVERY_TIME = Duration.ofDays(7);
    private final NovelDeliveryRepository deliveries;
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;
    private final NovelSunlightService sunlight;

    /** 회차가 새로 나오면 10명에게 배달한다. 목표 회차에 닿은 가지는 보내지 않는다. */
    public int deliverNewChapter(Novel n, NovelChapter c, Instant now) {
        if (!canGrow(n, c)) return 0;
        return send(n, c, Math.min(Novel.DELIVERY_COUNT, freeSlots(n, c)), now);
    }

    /** 넘기거나 시간이 지난 열매, 지워진 가지 하나만큼 다른 사람에게 다시 보낸다. 자리가 다 찼으면 보내지 않는다. */
    public int resend(Novel n, NovelChapter c, Instant now) {
        if (!canGrow(n, c)) return 0;
        return send(n, c, Math.min(1, freeSlots(n, c)), now);
    }

    /** 이 회차에 남은 가지 자리. */
    public int freeSlots(Novel n, NovelChapter c) {
        long taken = chapters.countGrowingChildren(c.getId())
                + deliveries.countByChapterIdAndStatus(c.getId(), NovelDeliveryStatus.DELIVERED);
        return (int) Math.max(0, n.getBranchLimit() - taken);
    }

    /** 내가 쓴 회차라 햇살을 내고 다음 회차를 이어 쓸 수 있는가. 앱은 이 값으로만 버튼을 보인다. */
    public boolean canSendToSelf(Novel n, NovelChapter c, Long me, Instant now) {
        return canGrow(n, c) && c.isWrittenBy(me)
                && deliveries.holdingIn(n.getId(), me, now).isEmpty() && freeSlots(n, c) > 0;
    }

    /** 내 글 잇기 — 햇살을 내고 내 회차를 나에게 배달한다. 그 뒤로는 받은 열매처럼 쓰고 발행한다. */
    public NovelDelivery sendToSelf(Novel n, NovelChapter c, Long me, Instant now) {
        if (!n.isOpen() || !c.isActive() || !c.isWrittenBy(me)) throw ApiException.of(ErrorCode.FORBIDDEN);
        if (!n.canGrowFrom(c.getChapterNumber())) throw ApiException.of(ErrorCode.NOVEL_CLOSED);
        if (deliveries.holdingIn(n.getId(), me, now).isPresent()) throw ApiException.of(ErrorCode.NOVEL_BRANCH_HOLDING);
        if (freeSlots(n, c) <= 0) throw ApiException.of(ErrorCode.NOVEL_BRANCH_FULL);
        int cost = sunlight.selfBranchCost();
        NovelDelivery d = deliveries.saveAndFlush(NovelDelivery.toSelf(n.getId(), c.getId(), me, now.plus(SELF_DELIVERY_TIME), cost));
        sunlight.spend(me, cost, NovelSunlightTransactionKind.SELF_BRANCH, "NOVEL_DELIVERY", d.getId());
        return d;
    }

    /**
     * 쓰지 않고 끝난 열매를 마무리한다. 내 글 잇기였으면 낸 햇살을 돌려주고 다시 보내지 않는다.
     * 무작위로 온 열매는 resendOthers 일 때 다른 한 사람에게 다시 보낸다.
     */
    public void settleUnwritten(Novel n, NovelDelivery d, Instant now, boolean resendOthers) {
        if (d.isSelf()) {
            sunlight.grant(d.getRecipientId(), d.getSunlightPaid(), NovelSunlightTransactionKind.REFUND, "NOVEL_DELIVERY", d.getId());
            return;
        }
        if (resendOthers) chapters.findById(d.getChapterId()).ifPresent(c -> resend(n, c, now));
    }

    /** 작품이 완결되면 아직 들고 있는 열매를 모두 거둔다(다시 보내지 않는다). 내 글 잇기의 햇살은 돌려준다. */
    public void closeAll(Novel n) {
        Instant now = Instant.now();
        for (NovelDelivery d : deliveries.findByNovelIdAndStatus(n.getId(), NovelDeliveryStatus.DELIVERED)) {
            d.expire();
            settleUnwritten(n, d, now, false);
        }
    }

    /** 가지가 수거되면 그 회차를 들고 있던 열매를 거둔다. */
    public void closeChapter(Novel n, NovelChapter c, Instant now) {
        for (NovelDelivery d : deliveries.findByChapterIdAndStatus(c.getId(), NovelDeliveryStatus.DELIVERED)) {
            d.expire();
            settleUnwritten(n, d, now, false);
        }
    }

    /** 배치가 마감이 지난 열매를 거두고 다른 사람에게 다시 보낸다. */
    @Transactional
    public void expireOverdue(Long deliveryId) {
        Instant now = Instant.now();
        NovelDelivery found = deliveries.findById(deliveryId).orElse(null);
        if (found == null) return;
        // 작품 → 배달 순서로 잠가 가지 발행·넘기기와 같은 순서를 지킨다.
        Novel n = novels.lockById(found.getNovelId()).orElse(null);
        NovelDelivery d = deliveries.lockById(deliveryId).orElse(null);
        if (n == null || d == null || !d.isOverdue(now)) return;
        d.expire();
        // 자리를 비운 뒤 다시 보내야 빈자리가 계산에 들어간다.
        deliveries.flush();
        settleUnwritten(n, d, now, true);
    }

    /** 연재 중인 OPEN 작품의, 나무에 있고 목표 회차에 닿지 않은 회차만 배달한다. */
    private static boolean canGrow(Novel n, NovelChapter c) {
        return n.isOpen() && c.isActive() && n.canGrowFrom(c.getChapterNumber());
    }
    private int send(Novel n, NovelChapter c, int count, Instant now) {
        if (count <= 0) return 0;
        Set<Long> excluded = new HashSet<>();
        excluded.add(n.getOwnerId());
        if (c.getAuthorId() != null) excluded.add(c.getAuthorId());
        List<Long> recipients = deliveries.pickRecipients(n.getId(), c.getId(), n.getGenres(), excluded, count);
        Instant dueAt = now.plus(Duration.ofHours(n.getTurnHours()));
        deliveries.saveAll(recipients.stream().map(id -> new NovelDelivery(n.getId(), c.getId(), id, dueAt)).toList());
        return recipients.size();
    }
}
