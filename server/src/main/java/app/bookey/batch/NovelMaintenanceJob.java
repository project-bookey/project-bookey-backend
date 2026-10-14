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
    @Scheduled(fixedDelay = 60000)
    public void advanceExpiredTurns() {
        for (Long id : novels.expiredIds(Instant.now(), PageRequest.of(0, 100))) {
            try { service.expire(id); } catch (RuntimeException e) { log.warn("소설 차례 넘기기 실패: id={}", id, e); }
        }
    }
    /** 마감이 지난 열매를 거두고 다른 사람에게 다시 배달한다. */
    @Scheduled(fixedDelay = 60000)
    public void resendOverdueDeliveries() {
        for (Long id : deliveries.overdueIds(Instant.now(), PageRequest.of(0, 100))) {
            try { deliveryService.expireOverdue(id); } catch (RuntimeException e) { log.warn("열매 다시 배달하기 실패: id={}", id, e); }
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
