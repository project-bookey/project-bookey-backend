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

    @Query("select d.id from NovelDelivery d where d.status = 'DELIVERED' and d.dueAt <= :now order by d.dueAt")
    List<Long> overdueIds(@Param("now") Instant now, Pageable pageable);

    /**
     * 열매를 받을 사람 고르기. 활동 중인 회원 가운데 뺄 사람(회차 작가·만든 사람)과
     * 이 작품의 열매를 이미 들고 있거나 이 회차를 한 번 받았던 사람을 뺀다.
     * 작품 장르를 관심 장르로 고른 사람 → 소설 관심 장르를 하나라도 고른 사람 → 나머지 순으로, 같은 묶음 안에서는 무작위로 고른다.
     */
    @Query(value = """
        SELECT u.id FROM users u
        WHERE u.status = 'ACTIVE' AND u.id NOT IN (:excluded)
          AND NOT EXISTS (SELECT 1 FROM novel_deliveries d WHERE d.novel_id = :novelId
                          AND d.recipient_id = u.id AND d.status = 'DELIVERED')
          AND NOT EXISTS (SELECT 1 FROM novel_deliveries d WHERE d.chapter_id = :chapterId AND d.recipient_id = u.id)
        ORDER BY EXISTS (SELECT 1 FROM novel_user_genres g WHERE g.user_id = u.id AND g.genre = :genre) DESC,
                 EXISTS (SELECT 1 FROM novel_user_genres g WHERE g.user_id = u.id) DESC,
                 random()
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> pickRecipients(@Param("novelId") Long novelId, @Param("chapterId") Long chapterId,
                              @Param("genre") String genre, @Param("excluded") Collection<Long> excluded,
                              @Param("limit") int limit);
}
