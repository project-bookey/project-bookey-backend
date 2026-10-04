package app.bookey.batch;

import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 오래 열린 세션 자동 종료 (§F3). 쉰 시간을 뺀 독서 시간이 4시간을 넘으면 4시간까지만 세어 닫고,
 * 4시간 넘게 쉬고 있으면 쉬기 시작한 시각에 끝난 것으로 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final ReadingSessionRepository sessionRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void closeStaleSessions() {
        Instant now = clock.instant();
        // 독서 시간이든 쉰 시간이든 4시간을 넘기려면 시작한 지 4시간은 지나야 한다.
        List<ReadingSession> candidates = sessionRepository.findStaleOpenSessions(now.minus(ReadingSession.MAX_SESSION));
        int closed = 0;
        for (ReadingSession session : candidates) {
            Instant endAt;
            if (session.activeDuration(now).compareTo(ReadingSession.MAX_SESSION) >= 0) {
                endAt = now; // close 가 4시간을 채운 때로 되돌려 자른다
            } else if (session.isPaused() && session.getPausedAt().isBefore(now.minus(ReadingSession.MAX_PAUSE))) {
                endAt = session.getPausedAt();
            } else {
                continue;
            }
            session.close(endAt, session.getEndPage(), null, 0, session.getMemo());
            closed++;
        }
        if (closed > 0) {
            log.info("SessionCleanupJob: {} stale sessions auto-closed", closed);
        }
    }
}
