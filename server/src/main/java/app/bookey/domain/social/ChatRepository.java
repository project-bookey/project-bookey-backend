package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** 정규화된 쌍(a < b)으로 조회한다 — aUserId 파생 쿼리는 프로퍼티 해석이 어긋나 JPQL 로 쓴다. */
    @Query("SELECT c FROM Chat c WHERE c.aUserId = :aUserId AND c.bUserId = :bUserId")
    Optional<Chat> findPair(@Param("aUserId") Long aUserId, @Param("bUserId") Long bUserId);

    /**
     * 내 채팅 목록 — 마지막 메시지 시각(없으면 개설 시각) 최신순. 상대가 탈퇴한(계정이 종료된) 방과
     * 내가 차단한 사람과의 방은 뺀다(나를 뺀 쪽이 상대다 — 나 자신은 차단할 수 없다).
     */
    @Query("""
            SELECT c FROM Chat c
            WHERE (c.aUserId = :userId OR c.bUserId = :userId)
              AND c.aUserId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
              AND c.bUserId NOT IN (SELECT t2.id FROM User t2 WHERE t2.status = 'TERMINATED')
              AND NOT EXISTS (SELECT b FROM UserBlock b
                              WHERE b.blockerId = :userId AND (b.blockedId = c.aUserId OR b.blockedId = c.bUserId))
            ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC, c.id DESC
            """)
    Page<Chat> findAllMine(@Param("userId") Long userId, Pageable pageable);
}
