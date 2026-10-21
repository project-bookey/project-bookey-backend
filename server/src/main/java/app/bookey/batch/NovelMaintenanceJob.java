package app.bookey.batch;

import app.bookey.api.novel.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Slf4j @Component @RequiredArgsConstructor
public class NovelMaintenanceJob {
    private final NovelRepository novels;
    private final NovelService service;
    private final NovelCoverRepository covers;
    private final NovelCoverService coverService;
    private final NovelDeliveryRepository deliveries;
    private final NovelDeliveryService deliveryService;
    private final NovelChapterRepository chapters;
    private final NovelBranchBinService binService;
    @Scheduled(fixedDelay = 60000)
    public void advanceExpiredTurns() {
        for (Long id : novels.expiredIds(Instant.now(), PageRequest.of(0, 100))) {
            try { service.expire(id); } catch (RuntimeException e) { log.warn("소설 차례 넘기기 실패: id={}", id, e); }
        }
    }
    /** 마감이 지난 자리를 거둔다. 자리가 비면 그 회차가 다시 홈의 열매 후보가 된다. */
    @Scheduled(fixedDelay = 60000)
    public void resendOverdueDeliveries() {
        for (Long id : deliveries.overdueIds(Instant.now(), PageRequest.of(0, 100))) {
            try { deliveryService.expireOverdue(id); } catch (RuntimeException e) { log.warn("마감 지난 열매 거두기 실패: id={}", id, e); }
        }
    }
    /** 한 달이 지나도 조회가 모자란 끝 가지를 나뭇가지 수거함으로 옮긴다. */
    @Scheduled(fixedDelay = 600000)
    public void pruneStaleBranches() {
        Instant judgedBefore = Instant.now().minus(NovelChapter.JUDGE_AFTER);
        for (Long id : chapters.staleTipIds(judgedBefore, NovelChapter.MIN_VIEWS, PageRequest.of(0, 100))) {
            try { binService.pruneIfStale(id); } catch (RuntimeException e) { log.warn("가지 수거 실패: id={}", id, e); }
        }
    }
    /** 수거함에서 일주일이 지난 가지를 지우고, 부모 회차에 빈자리만큼 다시 배달한다. */
    @Scheduled(fixedDelay = 600000)
    public void purgeBranchBin() {
        for (Long id : chapters.purgeDueIds(Instant.now(), PageRequest.of(0, 100))) {
            try { binService.purgeIfDue(id); } catch (RuntimeException e) { log.warn("수거함 가지 지우기 실패: id={}", id, e); }
        }
    }
    @Scheduled(cron = "0 25 4 * * *", zone = "Asia/Seoul")
    public void cleanCovers() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        for (Long id : covers.orphanIds(cutoff, PageRequest.of(0, 100))) {
            try { coverService.deleteOrphan(id, cutoff); } catch (RuntimeException e) { log.warn("소설 임시 표지 정리 실패: id={}", id, e); }
        }
    }
}
