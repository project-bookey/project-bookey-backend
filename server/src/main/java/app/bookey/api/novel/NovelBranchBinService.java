package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 나뭇가지 수거함 — 한 달이 지나도 조회가 50회를 못 넘긴 끝 가지를 나무에서 숨겨 글쓴이의 수거함에 둔다.
 * 글쓴이는 일주일 안에 햇살을 내고 살리거나 버린다. 고르지 않으면 지운다. 지워진 가지만큼 부모 회차에 자리가 생겨
 * 그 회차가 다시 홈의 열매 후보가 된다. 첫 작가의 줄기는 수거하지 않는다.
 * 작품을 먼저 잠그고 회차를 새로 읽는다(작품을 잠그는 가지 발행·넘기기와 같은 순서).
 */
@Service @RequiredArgsConstructor
public class NovelBranchBinService {
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;
    private final NovelDeliveryService delivery;
    private final NovelSunlightService sunlight;

    @Transactional(readOnly = true)
    public NovelBranchBinView bin(Long me) {
        List<NovelChapter> pruned = chapters.binOf(me);
        Map<Long, String> titles = novels.findAllById(pruned.stream().map(NovelChapter::getNovelId).distinct().toList())
                .stream().collect(Collectors.toMap(Novel::getId, Novel::getTitle));
        List<NovelBinBranchView> branches = pruned.stream().map(c -> new NovelBinBranchView(c.getId(), c.getNovelId(),
                titles.getOrDefault(c.getNovelId(), ""), c.getChapterNumber(), c.getTitle(),
                c.getBody().substring(0, Math.min(140, c.getBody().length())), c.getViewCount(),
                c.getPrunedAt(), c.getPurgeAt())).toList();
        return new NovelBranchBinView(branches, sunlight.restoreCost());
    }

    /** 햇살을 내고 나무로 되돌린다. 살린 날부터 한 달 뒤에 다시 판정한다. */
    @Transactional
    public NovelBranchBinView restore(Long me, Long chapterId) {
        Instant now = Instant.now();
        Locked locked = lockMine(me, chapterId, now);
        sunlight.spend(me, sunlight.restoreCost(), NovelSunlightTransactionKind.RESTORE_BRANCH, "NOVEL_CHAPTER", chapterId);
        locked.chapter().restore(now);
        locked.novel().addBranch();
        return bin(me);
    }

    /** 버리기 — 바로 지우고 부모 회차의 자리를 비운다. */
    @Transactional
    public NovelBranchBinView discard(Long me, Long chapterId) {
        Locked locked = lockMine(me, chapterId, Instant.now());
        locked.chapter().remove();
        return bin(me);
    }

    /** 배치 — 판정 기간이 지났는데 조회가 모자란 끝 가지를 수거함으로 옮긴다. 잠근 뒤 다시 확인한다. */
    @Transactional
    public void pruneIfStale(Long chapterId) {
        Instant now = Instant.now();
        Long novelId = chapters.novelIdOf(chapterId).orElse(null);
        if (novelId == null) return;
        Novel n = novels.lockById(novelId).orElse(null);
        NovelChapter c = chapters.findById(chapterId).orElse(null);
        if (n == null || c == null || !n.isOpen() || n.getStatus() != NovelStatus.ONGOING) return;
        if (!c.isStale(now) || !chapters.isTip(c.getId())) return;
        c.prune(now);
        n.hideBranch();
        delivery.closeChapter(c);
    }

    /** 배치 — 일주일 동안 고르지 않은 가지를 지운다. */
    @Transactional
    public void purgeIfDue(Long chapterId) {
        Instant now = Instant.now();
        Long novelId = chapters.novelIdOf(chapterId).orElse(null);
        if (novelId == null) return;
        Novel n = novels.lockById(novelId).orElse(null);
        NovelChapter c = chapters.findById(chapterId).orElse(null);
        if (n == null || c == null || !c.isPurgeDue(now)) return;
        c.remove();
    }

    private record Locked(Novel novel, NovelChapter chapter) {}
    private Locked lockMine(Long me, Long chapterId, Instant now) {
        Long novelId = chapters.novelIdOf(chapterId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Novel n = novels.lockById(novelId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        NovelChapter c = chapters.findById(chapterId).filter(found -> found.isWrittenBy(me))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // 지울 시각이 지났으면 배치가 아직 돌지 않았어도 이미 사라진 가지로 본다.
        if (!c.isPruned() || c.isPurgeDue(now)) throw ApiException.of(ErrorCode.NOVEL_BIN_GONE);
        return new Locked(n, c);
    }
}
