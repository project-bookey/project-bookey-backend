package app.bookey.batch;

import app.bookey.api.library.ProgressService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NudgeCopyWriter;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 완독 임박 알림 — 잔여 10% 이하 (§F5 알림 종류). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlmostDoneJob {

    private static final double REMAINING_THRESHOLD = 0.1;

    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final ProgressService progressService;
    private final NotificationService notificationService;
    private final NudgeCopyWriter copyWriter;

    @Scheduled(cron = "0 30 19 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        int scheduled = 0;
        for (User user : userRepository.findAll()) {
            for (ReadingRecord record :
                    recordRepository.findAllByUserIdAndStatus(user.getId(), ReadingStatus.READING)) {
                Book book = bookRepository.findById(record.getBookId()).orElse(null);
                var progress = progressService.calculate(record, book);
                if (progress.completionRate() == null
                        || progress.completionRate() < 1 - REMAINING_THRESHOLD
                        || progress.remainingPages() == 0) {
                    continue;
                }
                var copy = copyWriter.almostDone(
                        book == null ? "읽던 책" : book.getTitle(), progress.remainingPages());
                if (notificationService.schedule(new NotificationService.NotificationRequest(
                        user.getId(), NotificationType.ALMOST_DONE, null, record.getId(), null,
                        copy.title(), copy.body(),
                        Map.of("readingRecordId", record.getId()), null)).isPresent()) {
                    scheduled++;
                }
            }
        }
        log.info("AlmostDoneJob: {} notifications scheduled", scheduled);
    }
}
