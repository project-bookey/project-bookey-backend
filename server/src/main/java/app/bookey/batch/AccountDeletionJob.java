package app.bookey.batch;

import app.bookey.api.club.ClubService;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionJob {
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ClubService clubService;
    private final NotificationRepository notificationRepository;

    /*
     * 아래 두 작업은 탈퇴할 때 바로 하는 정리(AccountEraser) 가운데 남에게 간 알림 지우기와 클럽 나가기를 다시 한다 —
     * 그 처리가 들어오기 전에 탈퇴한 사람과 그때 실패한 경우를 메운다. 이미 정리됐으면 아무것도 바뀌지 않는다.
     */

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteNotificationsCausedByWithdrawnUsers() {
        notificationRepository.deleteAllCausedByWithdrawnUsers();
    }

    /** 사람마다 따로 처리한다 — 한 사람이 실패해도 다음 사람은 계속한다. */
    @Scheduled(cron = "0 5 4 * * *", zone = "Asia/Seoul")
    public void leaveClubsOfWithdrawnUsers() {
        for (var user : userRepository.findAllByStatusAndDeletionRequestedAtIsNotNull(UserStatus.TERMINATED)) {
            try {
                clubService.leaveAllOnWithdrawal(user.getId());
            } catch (RuntimeException e) {
                log.warn("Failed to leave clubs of withdrawn account: userId={}", user.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredAccounts() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        var users = userRepository.findAllByStatusAndDeletionRequestedAtLessThanEqual(UserStatus.TERMINATED, cutoff);
        for (var user : users) {
            Long userId = user.getId();
            // 공동 모임은 가장 오래된 활성 멤버에게 넘기고, 넘길 사람이 없는 모임만 함께 삭제한다.
            jdbcTemplate.update("""
                    WITH successors AS (
                        SELECT DISTINCT ON (c.id) c.id AS club_id, cm.user_id
                        FROM clubs c
                        JOIN club_members cm ON cm.club_id = c.id
                        JOIN users u ON u.id = cm.user_id
                        WHERE c.owner_id = ? AND cm.user_id <> ?
                          AND cm.status = 'ACTIVE' AND u.status <> 'TERMINATED'
                        ORDER BY c.id,
                                 CASE cm.role WHEN 'HOST' THEN 0 WHEN 'MODERATOR' THEN 1 ELSE 2 END,
                                 cm.joined_at, cm.id
                    )
                    UPDATE clubs c SET owner_id = s.user_id
                    FROM successors s WHERE c.id = s.club_id
                    """, userId, userId);
            jdbcTemplate.update("UPDATE club_members SET role = 'HOST' WHERE user_id IN (SELECT owner_id FROM clubs) AND role <> 'HOST'");
            jdbcTemplate.update("DELETE FROM clubs WHERE owner_id = ?", userId);
            userRepository.delete(user);
            log.info("Purged account after deletion grace period: userId={}", userId);
        }
    }
}
