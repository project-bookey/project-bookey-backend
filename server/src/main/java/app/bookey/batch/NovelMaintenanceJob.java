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
    @Scheduled(fixedDelay = 60000)
    public void advanceExpiredTurns() {
        for (Long id : novels.expiredIds(Instant.now(), PageRequest.of(0, 100))) {
            try { service.expire(id); } catch (RuntimeException e) { log.warn("소설 차례 넘기기 실패: id={}", id, e); }
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
