package app.bookey.api.library;

import app.bookey.api.library.dto.LibraryDtos.ProgressView;
import app.bookey.domain.book.Book;
import app.bookey.domain.reading.ProgressCalculator;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 진척도 계산 진입점 (§F4). 서재·모임·알림이 모두 이 결과를 공유한다. */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReadingSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public ProgressCalculator.Progress calculate(ReadingRecord record, Book book) {
        int totalPages = record.effectiveTotalPages(book == null ? null : book.getTotalPages());
        Instant since = Instant.now().minus(ProgressCalculator.PACE_WINDOW_DAYS, ChronoUnit.DAYS);
        long pagesInWindow = sessionRepository.sumPagesSince(record.getId(), since);
        return ProgressCalculator.calculate(record, totalPages, pagesInWindow,
                LocalDate.now(KST), Instant.now());
    }

    @Transactional(readOnly = true)
    public ProgressView toView(ReadingRecord record, Book book) {
        ProgressCalculator.Progress progress = calculate(record, book);
        long totalDuration = sessionRepository.sumDurationSec(record.getId());
        return new ProgressView(
                progress.currentPage(),
                progress.totalPages(),
                progress.completionRate(),
                progress.remainingPages(),
                progress.requiredDailyPace(),
                progress.actualDailyPace(),
                progress.paceGap(),
                progress.estimatedFinishDate(),
                progress.daysSinceLastRead(),
                progress.lagLevel().name(),
                totalDuration);
    }
}
