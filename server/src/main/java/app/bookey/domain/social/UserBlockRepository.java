package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** 내가 차단한 사람 — 최근에 막은 순. 탈퇴한(계정이 종료된) 사람은 뺀다. */
    @Query("""
            SELECT b FROM UserBlock b
            WHERE b.blockerId = :blockerId
              AND b.blockedId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY b.id DESC
            """)
    Page<UserBlock> findAllMine(@Param("blockerId") Long blockerId, Pageable pageable);
}
