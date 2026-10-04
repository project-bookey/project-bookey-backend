package app.bookey.batch;

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
