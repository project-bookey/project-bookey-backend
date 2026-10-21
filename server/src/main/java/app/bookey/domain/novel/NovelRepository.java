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

    /**
     * 열매 탭 목록 — 피드와 같은 작품 가운데 고른 장르 중 하나라도 작품 장르에 있고, 제목이나 만든 사람 닉네임에 검색어가 든 작품을 최신순으로.
     * 거르지 않는 조건은 빈 문자열로 넘긴다: status 는 상태 이름, genres 는 쉼표로 이은 장르, pattern 은 ! 로 이스케이프한 ILIKE 패턴.
     */
    @Query(value = """
        SELECT n.* FROM novels n
        WHERE n.kind = :kind AND n.is_public = TRUE AND n.chapter_count > 0
          AND (:status = '' OR n.status = :status)
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND (:genres = '' OR n.genres && string_to_array(:genres, ','))
          AND (:pattern = '' OR n.title ILIKE :pattern ESCAPE '!'
               OR n.owner_id IN (SELECT u.id FROM users u WHERE u.nickname ILIKE :pattern ESCAPE '!'))
        ORDER BY n.created_at DESC, n.id DESC
        """, countQuery = """
        SELECT count(*) FROM novels n
        WHERE n.kind = :kind AND n.is_public = TRUE AND n.chapter_count > 0
          AND (:status = '' OR n.status = :status)
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND (:genres = '' OR n.genres && string_to_array(:genres, ','))
          AND (:pattern = '' OR n.title ILIKE :pattern ESCAPE '!'
               OR n.owner_id IN (SELECT u.id FROM users u WHERE u.nickname ILIKE :pattern ESCAPE '!'))
        """, nativeQuery = true)
    Page<Novel> search(@Param("kind") String kind, @Param("status") String status, @Param("genres") String genres,
                       @Param("pattern") String pattern, Pageable pageable);

    @Query("""
        select n from Novel n where n.currentWriterId = :userId and n.status = 'ONGOING'
        and (n.turnDueAt is null or n.turnDueAt > :now)
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        order by n.turnDueAt asc, n.createdAt desc
        """)
    List<Novel> myTurns(@Param("userId") Long userId, @Param("now") Instant now, Pageable pageable);

    @Query("select n.id from Novel n where n.status = 'ONGOING' and n.turnDueAt <= :now")
    List<Long> expiredIds(@Param("now") Instant now, Pageable pageable);
    @Query("select n from Novel n where n.kind = :kind and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED') and (n.ownerId = :userId or n.id in (select m.novelId from NovelMember m where m.userId = :userId and m.status in ('ACTIVE', 'PENDING'))) order by n.createdAt desc")
    Page<Novel> mine(@Param("userId") Long userId, @Param("kind") NovelKind kind, Pageable pageable);
}
