package app.bookey.batch;

import app.bookey.admin.AdminUserService;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기간이 끝난 제재를 풀어 준다. 쓰기정지·정지 회원을 훑어 남은 제재로 상태를 다시 계산한다.
 * 회원마다 따로 처리해 한 명이 실패해도 나머지는 진행한다. 감사 로그는 관리자 행위만 남기므로 여기선 로그만 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionExpiryJob {

    private static final List<UserStatus> SANCTIONED =
            List.of(UserStatus.WRITE_BANNED, UserStatus.SUSPENDED, UserStatus.TERMINATED);

    private final UserRepository userRepository;
    private final AdminUserService adminUserService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 90 * 1000)
    public void releaseExpired() {
        int changed = 0;
        for (User user : userRepository.findAllByStatusInAndDeletionRequestedAtIsNull(SANCTIONED)) {
            try {
                if (adminUserService.reconcileStatus(user.getId())) {
                    changed++;
                }
            } catch (RuntimeException e) {
                log.warn("SanctionExpiryJob: failed for userId={}", user.getId(), e);
            }
        }
        if (changed > 0) {
            log.info("SanctionExpiryJob: {} users' sanction status updated", changed);
        }
    }
}
