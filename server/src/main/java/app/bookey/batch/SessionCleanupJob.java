package app.bookey.batch;

import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 4시간 초과 열린 세션 자동 종료 (§F3). suspect_idle 플래그가 붙어 검증에서 제외된다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final ReadingSessionRepository sessionRepository;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void closeStaleSessions() {
        Instant threshold = Instant.now().minus(ReadingSession.MAX_SESSION);
        List<ReadingSession> stale = sessionRepository.findStaleOpenSessions(threshold);
        for (ReadingSession session : stale) {
            session.close(session.getStartedAt().plus(ReadingSession.MAX_SESSION),
                    session.getEndPage(), null, 0, session.getMemo());
        }
        if (!stale.isEmpty()) {
            log.info("SessionCleanupJob: {} stale sessions auto-closed", stale.size());
        }
    }
}
