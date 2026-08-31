package app.bookey.batch;

import app.bookey.api.library.ProgressService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NudgeCopyWriter;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.*;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 지연 감지 → 재촉 알림 후보 생성 (§F5 스케줄링 아키텍처).
 * 매일 새벽 전 사용자 진척도를 재계산하고 발송 후보 큐를 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LagDetectionJob {

    private static final int BATCH_SIZE = 500;
    /** L1 진입 기준이 3일이므로 2일 초과부터 훑는다. */
    private static final int SCAN_DAYS = 2;

    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final ProgressService progressService;
    private final NotificationService notificationService;
    private final NudgeCopyWriter copyWriter;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        Instant threshold = Instant.now().minus(SCAN_DAYS, ChronoUnit.DAYS);
        List<ReadingRecord> candidates =
                recordRepository.findLagCandidates(threshold, PageRequest.of(0, BATCH_SIZE));

        int scheduled = 0;
        for (ReadingRecord record : candidates) {
            try {
                if (scheduleFor(record)) {
                    scheduled++;
                }
            } catch (Exception e) {
                log.error("Lag notification failed for record {}", record.getId(), e);
            }
        }
        log.info("LagDetectionJob: {} candidates, {} notifications scheduled",
                candidates.size(), scheduled);
    }

    private boolean scheduleFor(ReadingRecord record) {
        User user = userRepository.findById(record.getUserId()).orElse(null);
        if (user == null || !user.getStatus().canLogin()) {
            return false;
        }
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        var progress = progressService.calculate(record, book);
        LagLevel level = progress.lagLevel();
        if (!level.needsNotification()) {
            return false;
        }

        String title = book == null ? "읽던 책" : book.getTitle();
        long days = progress.daysSinceLastRead() == null ? 0 : progress.daysSinceLastRead();
        int variant = (int) (record.getId() + days);

        NudgeCopyWriter.Copy copy = copyWriter.write(
                user.getNotifyTone(), level, title, progress.remainingPages(), days,
                progress.estimatedFinishDate(), record.getTargetFinishDate(), variant);

        NotificationType type = switch (level) {
            case L4_NEGLECTED -> NotificationType.CLEANUP;
            case L3_SERIOUS -> NotificationType.MICRO_MISSION;
            default -> NotificationType.LAG;
        };

        return notificationService.schedule(new NotificationService.NotificationRequest(
                user.getId(), type, (short) level.getLevel(), record.getId(), null,
                copy.title(), copy.body(),
                Map.of("readingRecordId", record.getId(),
                       "lagLevel", level.name(),
                       "actions", List.of("PAUSE", "EXTEND_TARGET", "ABANDON")),
                user.getNotifyTone().name() + ":" + level.name())).isPresent();
    }
}
