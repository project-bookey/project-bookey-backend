package app.bookey.api.stats;

import app.bookey.api.session.dto.SessionDtos.DailyStat;
import app.bookey.api.session.dto.SessionDtos.StatsSummary;
import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** 통계 · 스트릭 (§F9, 탭3 기록). */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReadingSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public StatsSummary summary(Long userId, int days) {
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(Math.max(1, days) - 1L);
        Instant fromInstant = from.atStartOfDay(KST).toInstant();
        Instant toInstant = today.plusDays(1).atStartOfDay(KST).toInstant();

        List<ReadingSession> sessions = sessionRepository
                .findAllByUserIdAndStartedAtBetweenOrderByStartedAtAsc(userId, fromInstant, toInstant);

        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();   // [duration, pages, count]
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            buckets.put(d, new long[3]);
        }
        for (ReadingSession session : sessions) {
            LocalDate date = session.getStartedAt().atZone(KST).toLocalDate();
            long[] bucket = buckets.get(date);
            if (bucket == null) {
                continue;
            }
            bucket[0] += session.getDurationSec();
            Integer pages = session.readPages();
            bucket[1] += pages == null ? 0 : pages;
            bucket[2] += 1;
        }

        List<DailyStat> daily = buckets.entrySet().stream()
                .map(e -> new DailyStat(e.getKey().toString(), e.getValue()[0], e.getValue()[1],
                        (int) e.getValue()[2]))
                .toList();

        Set<LocalDate> readDays = new HashSet<>();
        buckets.forEach((date, bucket) -> {
            if (bucket[2] > 0) {
                readDays.add(date);
            }
        });

        return new StatsSummary(
                sessionRepository.sumDurationSecByUserSince(userId, Instant.EPOCH),
                bucketValue(buckets, today, 0),
                weekDuration(buckets, today),
                currentStreak(readDays, today),
                longestStreak(readDays),
                daily);
    }

    private long bucketValue(Map<LocalDate, long[]> buckets, LocalDate date, int index) {
        long[] bucket = buckets.get(date);
        return bucket == null ? 0 : bucket[index];
    }

    private long weekDuration(Map<LocalDate, long[]> buckets, LocalDate today) {
        long total = 0;
        for (int i = 0; i < 7; i++) {
            total += bucketValue(buckets, today.minusDays(i), 0);
        }
        return total;
    }

    /** 오늘(또는 어제)부터 거슬러 올라가며 끊기지 않은 일수. */
    static int currentStreak(Set<LocalDate> readDays, LocalDate today) {
        LocalDate cursor = readDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (readDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    static int longestStreak(Set<LocalDate> readDays) {
        List<LocalDate> sorted = new ArrayList<>(readDays);
        Collections.sort(sorted);
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : sorted) {
            current = (previous != null && previous.plusDays(1).equals(date)) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = date;
        }
        return longest;
    }
}
