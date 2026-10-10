package app.bookey.domain.novel;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface NovelRepository extends JpaRepository<Novel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Novel n where n.id = :id")
    Optional<Novel> lockById(@Param("id") Long id);

    @Query("""
        select n from Novel n where n.kind = :kind and n.isPublic = true
        and (:status is null or n.status = :status)
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        order by n.createdAt desc, n.id desc
        """)
    Page<Novel> feed(@Param("kind") NovelKind kind, @Param("status") NovelStatus status, Pageable pageable);

    @Query("""
        select n from Novel n where n.currentWriterId = :userId and n.status = 'ONGOING'
        and (n.turnDueAt is null or n.turnDueAt > :now)
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        order by n.turnDueAt asc, n.createdAt desc
        """)
    List<Novel> myTurns(@Param("userId") Long userId, @Param("now") Instant now, Pageable pageable);

    @Query("select n.id from Novel n where n.status = 'ONGOING' and n.turnDueAt <= :now")
    List<Long> expiredIds(@Param("now") Instant now, Pageable pageable);
    Optional<Novel> findByInviteCode(String inviteCode);
    @Query("select n from Novel n where n.kind = :kind and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED') and (n.ownerId = :userId or n.id in (select m.novelId from NovelMember m where m.userId = :userId and m.status in ('ACTIVE', 'PENDING'))) order by n.createdAt desc")
    Page<Novel> mine(@Param("userId") Long userId, @Param("kind") NovelKind kind, Pageable pageable);
}
