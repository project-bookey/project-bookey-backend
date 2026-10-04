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

    /** 탈퇴 — 그 회원의 알림을 모두 지운다(푸시 발송 기록은 FK CASCADE 로 함께). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    int deleteAllByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    /**
     * 탈퇴 — 그 회원 때문에 다른 사람에게 간 알림을 지운다. '○○님이 좋아요를 눌렀어요'처럼 본문에 닉네임이 굳어 있어
     * 계정을 익명화해도 남는다. 보낸 사람은 payload 의 fromUserId · commenterId(독후감 댓글), 팔로우 알림의 userId 에 있다.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            DELETE FROM notifications
            WHERE payload->>'fromUserId' = CAST(:userId AS text)
               OR payload->>'commenterId' = CAST(:userId AS text)
               OR (type IN ('FOLLOWED', 'FOLLOW_CONNECTED') AND payload->>'userId' = CAST(:userId AS text))
            """, nativeQuery = true)
    int deleteAllCausedBy(@Param("userId") Long userId);

    /** deleteAllCausedBy 를 아직 계정 행이 남은 탈퇴자 모두에게 — 그 처리가 들어오기 전에 탈퇴한 사람의 알림을 메운다. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            DELETE FROM notifications n
            USING users u
            WHERE u.status = 'TERMINATED' AND u.deletion_requested_at IS NOT NULL
              AND (n.payload->>'fromUserId' = CAST(u.id AS text)
                OR n.payload->>'commenterId' = CAST(u.id AS text)
                OR (n.type IN ('FOLLOWED', 'FOLLOW_CONNECTED') AND n.payload->>'userId' = CAST(u.id AS text)))
            """, nativeQuery = true)
    int deleteAllCausedByWithdrawnUsers();
}
