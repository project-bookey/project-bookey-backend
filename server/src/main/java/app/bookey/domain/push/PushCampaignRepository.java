package app.bookey.domain.push;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PushCampaignRepository extends JpaRepository<PushCampaign, Long> {

    Page<PushCampaign> findAllByOrderByIdDesc(Pageable pageable);

    List<PushCampaign> findAllByStatusAndScheduledAtLessThanEqualOrderByIdAsc(PushCampaignStatus status, Instant now);

    List<PushCampaign> findAllByStatusOrderByIdAsc(PushCampaignStatus status);

    /** 예약을 '보내는 중' 으로 가져간다 — 여러 서버가 같은 캠페인을 동시에 시작하지 않게 한 번만 성공한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PushCampaign c SET c.status = 'SENDING', c.startedAt = :now, c.version = c.version + 1
            WHERE c.id = :id AND c.status = 'SCHEDULED' AND c.scheduledAt <= :now
            """)
    int claim(@Param("id") Long id, @Param("now") Instant now);

    /**
     * 캠페인 대상자 — 로그인할 수 있고 탈퇴를 신청하지 않은 회원, 광고면 광고성 정보 수신에 마지막으로 동의한 회원.
     * 회원 id 순으로 cursor 다음부터 limit 명.
     */
    @Query(value = """
            SELECT u.id FROM users u
            WHERE u.id > :cursor
              AND u.status IN ('ACTIVE', 'WRITE_BANNED') AND u.deletion_requested_at IS NULL
              AND (:marketing = FALSE OR COALESCE((
                    SELECT c.agreed FROM user_consents c
                    WHERE c.user_id = u.id AND c.kind = 'MARKETING'
                    ORDER BY c.id DESC LIMIT 1), FALSE))
            ORDER BY u.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findAudience(@Param("cursor") long cursor, @Param("marketing") boolean marketing,
                            @Param("limit") int limit);

    @Query(value = """
            SELECT COUNT(*) FROM users u
            WHERE u.status IN ('ACTIVE', 'WRITE_BANNED') AND u.deletion_requested_at IS NULL
              AND (:marketing = FALSE OR COALESCE((
                    SELECT c.agreed FROM user_consents c
                    WHERE c.user_id = u.id AND c.kind = 'MARKETING'
                    ORDER BY c.id DESC LIMIT 1), FALSE))
            """, nativeQuery = true)
    long countAudience(@Param("marketing") boolean marketing);

    /** 대상자 중 푸시를 받을 기기가 있는 회원 — 나머지는 앱 알림 목록에만 남는다. */
    @Query(value = """
            SELECT COUNT(*) FROM users u
            WHERE u.status IN ('ACTIVE', 'WRITE_BANNED') AND u.deletion_requested_at IS NULL
              AND EXISTS (SELECT 1 FROM user_devices d WHERE d.user_id = u.id AND d.push_enabled)
              AND (:marketing = FALSE OR COALESCE((
                    SELECT c.agreed FROM user_consents c
                    WHERE c.user_id = u.id AND c.kind = 'MARKETING'
                    ORDER BY c.id DESC LIMIT 1), FALSE))
            """, nativeQuery = true)
    long countAudienceWithDevice(@Param("marketing") boolean marketing);
}
