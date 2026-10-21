package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/**
 * 자리를 맡은 열매에서 가지를 뻗어 이어 쓰기. 맡은 회차가 부모가 되고 깊이는 부모보다 1 크다.
 * 초안은 작품·사람마다 하나(NovelDraft)라 turnNumber 자리에 열매 id 를 넣어 다른 열매의 원고와 섞이지 않게 한다.
 */
@Service @RequiredArgsConstructor
public class NovelBranchService {
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;
    private final NovelDraftRepository drafts;
    private final NovelDeliveryRepository deliveries;
    private final NovelDeliveryService delivery;
    private final NovelSunlightService sunlight;
    private final UserRepository users;
    private final RateLimiter limiter;

    @Transactional(readOnly = true)
    public NovelDraftView draft(Long me, Long deliveryId) {
        NovelDelivery d = held(me, deliveryId, false);
        return drafts.findByNovelIdAndUserId(d.getNovelId(), me).filter(saved -> saved.getTurnNumber() == d.getId())
                .map(saved -> new NovelDraftView(saved.getTurnNumber(), saved.getTitle(), saved.getBody(), saved.getUpdatedAt()))
                .orElseGet(() -> new NovelDraftView(d.getId(), "", "", null));
    }

    @Transactional
    public NovelDraftView saveDraft(Long me, Long deliveryId, NovelBranchWriteRequest request) {
        NovelDelivery d = held(me, deliveryId, true);
        NovelDraft saved = drafts.findByNovelIdAndUserId(d.getNovelId(), me).orElseGet(() -> new NovelDraft(d.getNovelId(), me));
        saved.save(d.getId(), request.title(), request.body());
        drafts.saveAndFlush(saved);
        return new NovelDraftView(saved.getTurnNumber(), saved.getTitle(), saved.getBody(), saved.getUpdatedAt());
    }

    /** 가지를 발행하면 그 회차도 자리가 남는 동안 다른 사람의 열매가 된다. 발행은 한 번뿐이라 같은 열매로 두 번 쓸 수 없다. */
    @Transactional
    public NovelChapterView publish(Long me, Long deliveryId, NovelBranchWriteRequest request) {
        if (request.title().isBlank() || request.body().isBlank()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        limiter.require("novel:branch:" + me, 10, Duration.ofMinutes(1));
        Instant now = Instant.now();
        NovelDelivery found = deliveries.findById(deliveryId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Novel n = novels.lockById(found.getNovelId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        NovelDelivery d = held(me, deliveryId, true);
        NovelChapter parent = chapters.findById(d.getChapterId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!parent.isActive() || !n.canGrowFrom(parent.getChapterNumber())) throw ApiException.of(ErrorCode.NOVEL_DELIVERY_GONE);
        NovelChapter branch = chapters.saveAndFlush(new NovelChapter(n.getId(), me, parent.getId(),
                parent.getChapterNumber() + 1, request.title(), request.body()));
        d.written(branch.getId());
        drafts.findByNovelIdAndUserId(n.getId(), me).ifPresent(drafts::delete);
        n.addBranch();
        return new NovelChapterView(branch.getId(), n.getId(), branch.getChapterNumber(), branch.getTitle(),
                branch.getBody(), nickname(me), branch.getCreatedAt(), branch.getParentChapterId(), branch.getViewCount(),
                delivery.canSendToSelf(n, branch, me, now), sunlight.selfBranchCost(), branch.isTrunk(),
                delivery.canClaim(n, branch, me, now), delivery.freeSlots(n, branch));
    }

    /** 내려놓기 — 맡은 자리를 비운다. 그 회차는 다시 다른 사람의 열매가 된다. 내 글 잇기였으면 낸 햇살을 돌려받는다. */
    @Transactional
    public void decline(Long me, Long deliveryId) {
        NovelDelivery found = deliveries.findById(deliveryId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Novel n = novels.lockById(found.getNovelId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        NovelDelivery d = held(me, deliveryId, true);
        d.decline();
        drafts.findByNovelIdAndUserId(n.getId(), me).filter(saved -> saved.getTurnNumber() == d.getId()).ifPresent(drafts::delete);
        delivery.settleUnwritten(d);
    }

    private NovelDelivery held(Long me, Long deliveryId, boolean lock) {
        NovelDelivery d = (lock ? deliveries.lockById(deliveryId) : deliveries.findById(deliveryId))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!d.getRecipientId().equals(me)) throw ApiException.of(ErrorCode.NOT_FOUND);
        Novel n = novels.findById(d.getNovelId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (users.isTerminated(n.getOwnerId())) throw ApiException.of(ErrorCode.NOT_FOUND);
        if (!d.isHeldBy(me, Instant.now()) || n.getStatus() != NovelStatus.ONGOING)
            throw ApiException.of(ErrorCode.NOVEL_DELIVERY_GONE);
        return d;
    }
    private String nickname(Long id) {
        return users.findById(id).map(User::getNickname).orElse("탈퇴한 작가");
    }
}
