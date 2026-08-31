package app.bookey.api.review;

import app.bookey.domain.book.Book;
import app.bookey.domain.book.GenreKey;
import app.bookey.domain.reading.*;
import app.bookey.domain.review.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증 등급 산정 파이프라인 (§8.2).
 *
 * <pre>
 * ① 누적 커버리지 = Σ(고유 페이지 구간) / 총 페이지     ← 중복 구간 제거
 * ② 타이머 세션 수, 총 타이머 시간 집계 (MANUAL 세션은 시간의 40%만 인정)
 * ③ 최소 요구 시간(장르 계수 적용) 대비 충족 여부
 * ④ 어뷰징 신호 스캔 (§8.3)
 * </pre>
 *
 * 결과는 리뷰 작성 시점의 스냅샷으로 고정된다. 사후에 세션을 조작해도 소급 상승은 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    /** 최소 요구 시간(분) = 총 페이지 × 0.7분/쪽 × 0.35 × 장르 계수 */
    private static final double MINUTES_PER_PAGE = 0.7;
    private static final double SPEED_ALLOWANCE = 0.35;

    private static final double FULL_COVERAGE = 0.9;
    private static final int FULL_TIMER_SESSIONS = 3;
    private static final double PARTIAL_COVERAGE = 0.5;
    private static final int PARTIAL_TIMER_SESSIONS = 2;

    /** 24h 내 완독 5권 초과 → 계정 검토 큐 (§8.3). */
    private static final int BULK_FINISH_THRESHOLD = 5;

    private final ReadingSessionRepository sessionRepository;
    private final ReadingRecordRepository recordRepository;

    public record VerificationResult(
            VerificationLevel level,
            double coverage,
            int timerSessionCount,
            long verifiedMinutes,
            long requiredMinutes,
            List<String> flags
    ) {
        public Map<String, Object> toSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("coverage", Math.round(coverage * 1000) / 1000.0);
            snapshot.put("timerSessionCount", timerSessionCount);
            snapshot.put("verifiedMinutes", verifiedMinutes);
            snapshot.put("requiredMinutes", requiredMinutes);
            snapshot.put("flags", flags);
            snapshot.put("evaluatedAt", Instant.now().toString());
            return snapshot;
        }
    }

    @Transactional(readOnly = true)
    public VerificationResult evaluate(ReadingRecord record, Book book) {
        int totalPages = record.effectiveTotalPages(book == null ? null : book.getTotalPages());
        List<String> flags = new ArrayList<>();

        List<ReadingSession> sessions = sessionRepository.findPageRanges(record.getId());

        // ① 고유 페이지 구간 병합
        long uniquePages = mergeUniquePages(sessions);
        double coverage = totalPages > 0 ? Math.min(1.0, (double) uniquePages / totalPages) : 0;

        // ② 세션 집계 — 어뷰징 플래그가 붙은 세션은 시간이 0으로 계산된다
        int timerSessions = (int) sessionRepository
                .countByReadingRecordIdAndSource(record.getId(), SessionSource.TIMER);
        long verifiedSeconds = sessionRepository
                .findAllByReadingRecordIdOrderByStartedAtDesc(record.getId()).stream()
                .mapToLong(ReadingSession::verifiedDurationSec)
                .sum();
        long verifiedMinutes = verifiedSeconds / 60;

        // ③ 최소 요구 시간
        long requiredMinutes = requiredMinutes(totalPages, book);

        // ④ 어뷰징 신호
        if (verifiedMinutes < requiredMinutes * 0.5 && record.getStatus() == ReadingStatus.FINISHED) {
            flags.add("instant_finish");   // 순간 완독 (§8.3)
        }
        long recentFinishes = recordRepository.countFinishedSince(
                record.getUserId(), Instant.now().minus(24, ChronoUnit.HOURS));
        if (recentFinishes > BULK_FINISH_THRESHOLD) {
            flags.add("bulk_finish");
        }
        sessions.stream()
                .flatMap(s -> s.getAbuseFlags().stream())
                .distinct()
                .forEach(flags::add);

        VerificationLevel level = decide(coverage, timerSessions, verifiedMinutes,
                requiredMinutes, flags);

        return new VerificationResult(level, coverage, timerSessions, verifiedMinutes,
                requiredMinutes, flags.stream().distinct().toList());
    }

    private VerificationLevel decide(double coverage, int timerSessions, long verifiedMinutes,
                                     long requiredMinutes, List<String> flags) {
        if (flags.contains("instant_finish") || flags.contains("bulk_finish")) {
            return VerificationLevel.FLAGGED;
        }
        if (coverage >= FULL_COVERAGE
                && timerSessions >= FULL_TIMER_SESSIONS
                && verifiedMinutes >= requiredMinutes) {
            return VerificationLevel.VERIFIED_FULL;
        }
        if (coverage >= PARTIAL_COVERAGE && timerSessions >= PARTIAL_TIMER_SESSIONS) {
            return VerificationLevel.VERIFIED_PARTIAL;
        }
        return VerificationLevel.UNVERIFIED;
    }

    /** 장르 계수를 적용한 최소 요구 시간(분). 예) 300쪽 일반서 → 약 73분 */
    public long requiredMinutes(int totalPages, Book book) {
        if (totalPages <= 0) {
            return 0;
        }
        GenreKey genre = book == null ? GenreKey.GENERAL : book.getGenreKey();
        return Math.round(totalPages * MINUTES_PER_PAGE * SPEED_ALLOWANCE * genre.getCoefficient());
    }

    /** 중복 구간을 제거한 고유 페이지 수. 구간 병합(merge intervals). */
    static long mergeUniquePages(List<ReadingSession> sessions) {
        List<int[]> ranges = sessions.stream()
                .filter(s -> s.getStartPage() != null && s.getEndPage() != null)
                .filter(ReadingSession::isCountedForVerification)
                .map(s -> new int[]{s.getStartPage(), s.getEndPage()})
                .filter(r -> r[1] > r[0])
                .sorted((a, b) -> Integer.compare(a[0], b[0]))
                .toList();

        long total = 0;
        int currentStart = -1;
        int currentEnd = -1;
        for (int[] range : ranges) {
            if (currentStart < 0) {
                currentStart = range[0];
                currentEnd = range[1];
                continue;
            }
            if (range[0] <= currentEnd) {
                currentEnd = Math.max(currentEnd, range[1]);
            } else {
                total += currentEnd - currentStart;
                currentStart = range[0];
                currentEnd = range[1];
            }
        }
        if (currentStart >= 0) {
            total += currentEnd - currentStart;
        }
        return total;
    }
}
