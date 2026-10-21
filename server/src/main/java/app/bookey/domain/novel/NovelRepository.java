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
        select n from Novel n where n.kind = :kind and n.isPublic = true and n.chapterCount > 0
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

    /**
     * 홈 열매 밭에 그대로 보여 줄 수 있는 작품 — 비공개가 됐거나 만든 사람이 탈퇴한 작품, 아직 회차가 없는 작품은 뺀다.
     * 지워진 작품은 저절로 빠진다. 피드·새로고침도 회차 없는 작품은 고르지 않는다(작품을 만들 때 1회차를 함께 올리기 전에 만든 작품).
     */
    @Query("""
        select n from Novel n where n.id in :ids and n.isPublic = true and n.chapterCount > 0
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        """)
    List<Novel> findShowableOnHome(@Param("ids") Collection<Long> ids);

    /**
     * 홈 열매 밭 새로고침에 놓을 작품 고르기. 피드와 같은 작품(공개 릴레이, 만든 사람이 탈퇴하지 않음) 가운데 뺄 작품을 빼고,
     * 내 소설 관심 장르가 작품 장르 가운데 하나라도 맞는 작품 → 나머지 순으로, 같은 묶음 안에서는 무작위로 고른다. 상태와 내 작품은 피드처럼 가리지 않는다.
     */
    default List<Long> pickForHomeFeed(Long userId, Collection<Long> excluded, int limit) {
        // NOT IN 에 빈 목록을 넘기면 (null) 로 펼쳐져 아무 작품도 고르지 못한다 — 없는 id 0 을 늘 함께 넘긴다.
        List<Long> excludedOrNone = new ArrayList<>(excluded);
        excludedOrNone.add(0L);
        return pickForHomeFeedExcluding(userId, excludedOrNone, limit);
    }

    /** {@link #pickForHomeFeed} 를 쓴다 — excluded 가 비어 있으면 아무것도 고르지 못한다. */
    @Query(value = """
        SELECT n.id FROM novels n
        WHERE n.kind = 'RELAY' AND n.is_public = TRUE AND n.chapter_count > 0
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND n.id NOT IN (:excluded)
        ORDER BY EXISTS (SELECT 1 FROM novel_user_genres g WHERE g.user_id = :userId AND g.genre = ANY(n.genres)) DESC,
                 random()
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> pickForHomeFeedExcluding(@Param("userId") Long userId, @Param("excluded") Collection<Long> excluded,
                                        @Param("limit") int limit);

    @Query("select n.id from Novel n where n.status = 'ONGOING' and n.turnDueAt <= :now")
    List<Long> expiredIds(@Param("now") Instant now, Pageable pageable);
    @Query("select n from Novel n where n.kind = :kind and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED') and (n.ownerId = :userId or n.id in (select m.novelId from NovelMember m where m.userId = :userId and m.status in ('ACTIVE', 'PENDING'))) order by n.createdAt desc")
    Page<Novel> mine(@Param("userId") Long userId, @Param("kind") NovelKind kind, Pageable pageable);
}
