package app.bookey.domain.novel;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface NovelDeliveryRepository extends JpaRepository<NovelDelivery, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NovelDelivery d where d.id = :id")
    Optional<NovelDelivery> lockById(@Param("id") Long id);

    /** 내가 들고 있는 열매 — 마감 전이고 작품이 연재 중이며 만든 사람이 탈퇴하지 않은 것. */
    @Query("""
        select d from NovelDelivery d where d.recipientId = :userId and d.status = 'DELIVERED' and d.dueAt > :now
        and d.novelId in (select n.id from Novel n where n.status = 'ONGOING'
            and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED'))
        order by d.createdAt desc, d.id desc
        """)
    List<NovelDelivery> holding(@Param("userId") Long userId, @Param("now") Instant now, Pageable pageable);

    @Query("""
        select d from NovelDelivery d where d.novelId = :novelId and d.recipientId = :userId
        and d.status = 'DELIVERED' and d.dueAt > :now
        """)
    Optional<NovelDelivery> holdingIn(@Param("novelId") Long novelId, @Param("userId") Long userId, @Param("now") Instant now);

    List<NovelDelivery> findByNovelIdAndStatus(Long novelId, NovelDeliveryStatus status);
    List<NovelDelivery> findByChapterIdAndStatus(Long chapterId, NovelDeliveryStatus status);
    /** 이 회차에 자리를 맡은 사람 수 — 쓰면 가지가 되므로 가지 자리를 미리 차지한다. */
    long countByChapterIdAndStatus(Long chapterId, NovelDeliveryStatus status);

    @Query("select d.id from NovelDelivery d where d.status = 'DELIVERED' and d.dueAt <= :now order by d.dueAt")
    List<Long> overdueIds(@Param("now") Instant now, Pageable pageable);
}
