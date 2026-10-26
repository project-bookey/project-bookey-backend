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

    /**
     * 열매 탭 순위 — since 뒤로 이어 쓴 회차(뿌리가 아닌, 나무에 있는, 탈퇴하지 않은 사람이 쓴 회차)가 많은 공개 작품 순.
     * 수가 같으면 더 최근에 이어 쓴 작품이 앞이다. 거르지 않는 장르는 빈 문자열로 넘긴다(쉼표로 이은 장르, 하나라도 맞으면).
     */
    @Query(value = """
        SELECT n.* FROM novels n
        JOIN (SELECT c.novel_id, count(*) AS added, max(c.created_at) AS latest FROM novel_chapters c
              WHERE c.state = 'ACTIVE' AND c.parent_chapter_id IS NOT NULL AND c.created_at >= :since
                AND c.author_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
              GROUP BY c.novel_id) recent ON recent.novel_id = n.id
        WHERE n.kind = :kind AND n.is_public = TRUE
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND (:genres = '' OR n.genres && string_to_array(:genres, ','))
        ORDER BY recent.added DESC, recent.latest DESC, n.id DESC
        LIMIT :size
        """, nativeQuery = true)
    List<Novel> mostContinuedSince(@Param("kind") String kind, @Param("genres") String genres,
                                   @Param("since") Instant since, @Param("size") int size);

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

    /**
     * 내가 이어 쓴 이야기 — 남의 작품에 내가 쓴 회차(나무에 있는)가 있는 작품을 내가 가장 최근에 쓴 순으로.
     * 내 작품과 참가한 모여 만들기(mine)는 빼서 마이의 내 작품과 겹치지 않게 한다.
     */
    @Query(value = """
        SELECT n.* FROM novels n
        JOIN (SELECT c.novel_id, max(c.created_at) AS latest FROM novel_chapters c
              WHERE c.author_id = :userId AND c.state = 'ACTIVE'
              GROUP BY c.novel_id) written ON written.novel_id = n.id
        WHERE n.kind = :kind AND n.owner_id <> :userId
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND n.id NOT IN (SELECT m.novel_id FROM novel_members m WHERE m.user_id = :userId AND m.status IN ('ACTIVE', 'PENDING'))
        ORDER BY written.latest DESC, n.id DESC
        """, countQuery = """
        SELECT count(*) FROM novels n
        WHERE n.kind = :kind AND n.owner_id <> :userId
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND n.id NOT IN (SELECT m.novel_id FROM novel_members m WHERE m.user_id = :userId AND m.status IN ('ACTIVE', 'PENDING'))
          AND n.id IN (SELECT c.novel_id FROM novel_chapters c WHERE c.author_id = :userId AND c.state = 'ACTIVE')
        """, nativeQuery = true)
    Page<Novel> continuedBy(@Param("userId") Long userId, @Param("kind") String kind, Pageable pageable);
}
