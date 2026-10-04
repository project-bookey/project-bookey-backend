package app.bookey.batch;

import app.bookey.domain.user.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 이메일 인증 기록 정리 — 만료된 지 30일이 지난 코드 기록(이메일 주소 포함)을 지운다.
 * 개인정보처리방침의 '이메일 인증 기록 30일' 보관 기간과 같다. 30일이면 재발급 쿨다운·시도 제한 판단에는 충분하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationCleanupJob {

    static final Duration RETENTION = Duration.ofDays(30);

    private final EmailVerificationRepository repository;

    /** 문의 사진 정리(04:40) 10분 뒤. */
    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        run(Instant.now());
    }

    /** 테스트·수동 호출용. 지운 행 수를 돌려준다. */
    public int run(Instant now) {
        int deleted = repository.deleteAllExpiredBefore(now.minus(RETENTION));
        log.info("EmailVerificationCleanupJob: {} expired verifications deleted", deleted);
        return deleted;
    }
}
