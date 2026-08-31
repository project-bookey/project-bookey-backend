package app.bookey.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.sentAt IS NULL AND n.scheduledAt <= :now")
    List<Notification> findDueForSend(@Param("now") Instant now, Pageable pageable);

    /** 개인 알림 일일 한도 검사 (§F5 총량 제한). */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.userId = :userId AND n.clubId IS NULL
              AND n.scheduledAt >= :from AND n.scheduledAt < :to
            """)
    long countPersonalScheduled(@Param("userId") Long userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /** 모임 알림 일일 한도 — 전체 3건, 모임당 1건 (§12.4). */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.userId = :userId AND n.clubId IS NOT NULL
              AND n.scheduledAt >= :from AND n.scheduledAt < :to
            """)
    long countClubScheduled(@Param("userId") Long userId,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.userId = :userId AND n.clubId = :clubId
              AND n.scheduledAt >= :from AND n.scheduledAt < :to
            """)
    long countClubScheduledForClub(@Param("userId") Long userId,
                                   @Param("clubId") Long clubId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    /** 무반응 3회 연속 판정 — 최근 발송 이력(§F5 에스컬레이션 & 쿨다운). */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.type = :type AND n.sentAt IS NOT NULL
            ORDER BY n.sentAt DESC
            """)
    List<Notification> findRecentSent(@Param("userId") Long userId,
                                      @Param("type") NotificationType type,
                                      Pageable pageable);

    /** 전환 마킹 대상 — 24h 내 발송됐고 아직 전환 안 된 알림. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.convertedAt IS NULL
              AND n.sentAt IS NOT NULL AND n.sentAt >= :since
            """)
    List<Notification> findConvertible(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.sentAt >= :since AND n.convertedAt IS NOT NULL
            """)
    long countConvertedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.sentAt >= :since")
    long countSentSince(@Param("since") Instant since);
}
