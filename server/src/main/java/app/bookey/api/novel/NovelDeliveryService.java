package app.bookey.api.novel;

import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/**
 * 열매 배달 — 열매로 퍼뜨리는 작품(OPEN)의 회차를 무작위로 고른 사람에게 보낸다.
 * 부르는 쪽이 작품을 잠근(lockById) 트랜잭션 안에서 불러, 같은 작품의 배달이 겹치지 않게 한다.
 */
@Service @RequiredArgsConstructor
public class NovelDeliveryService {
    private final NovelDeliveryRepository deliveries;
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;

    /** 회차가 새로 나오면 작품에 정한 인원에게 배달한다. 목표 회차에 닿은 가지는 보내지 않는다. */
    public int deliverNewChapter(Novel n, NovelChapter c, Instant now) {
        return send(n, c, n.getDeliveryCount() == null ? 0 : n.getDeliveryCount(), now);
    }

    /** 넘기거나 시간이 지난 열매 하나를 다른 사람에게 다시 보낸다. */
    public int resend(Novel n, NovelChapter c, Instant now) { return send(n, c, 1, now); }

    /** 작품이 완결되면 아직 들고 있는 열매를 모두 거둔다(다시 보내지 않는다). */
    public void closeAll(Long novelId) {
        deliveries.findByNovelIdAndStatus(novelId, NovelDeliveryStatus.DELIVERED).forEach(NovelDelivery::expire);
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
        chapters.findById(d.getChapterId()).ifPresent(c -> resend(n, c, now));
    }

    private int send(Novel n, NovelChapter c, int count, Instant now) {
        if (!n.isOpen() || count <= 0 || !n.canGrowFrom(c.getChapterNumber())) return 0;
        Set<Long> excluded = new HashSet<>();
        excluded.add(n.getOwnerId());
        if (c.getAuthorId() != null) excluded.add(c.getAuthorId());
        List<Long> recipients = deliveries.pickRecipients(n.getId(), c.getId(), n.getGenre(), excluded, count);
        Instant dueAt = now.plus(Duration.ofHours(n.getTurnHours()));
        deliveries.saveAll(recipients.stream().map(id -> new NovelDelivery(n.getId(), c.getId(), id, dueAt)).toList());
        return recipients.size();
    }
}
