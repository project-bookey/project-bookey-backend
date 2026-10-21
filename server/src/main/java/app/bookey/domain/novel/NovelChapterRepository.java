package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 읽는 쪽 조회는 나무에 있는(ACTIVE) 회차만 준다. 수거함의 가지는 글쓴이의 수거함에서만 보인다. */
public interface NovelChapterRepository extends JpaRepository<NovelChapter, Long> {
    /**
     * 그 사람이 지금 이어 쓸 수 있는 회차(c, 작품 n) — 연재 중인 공개 OPEN 작품의 나무에 있는 회차 가운데 목표 회차에 닿지 않았고
     * 가지 자리가 남은 것. 내 작품·내가 쓴 회차·탈퇴한 사람의 것은 빼고(내 작품은 줄기로, 내 회차는 햇살로 잇는다),
     * 이 작품에서 이미 자리를 맡고 있으면 뺀다(한 작품에 자리는 하나). 내가 이미 이어 쓴 회차도 뺀다(열매가 다시 떨어지지 않게).
     * 가지 자리는 {@code NovelDeliveryService.freeSlots} 와 같이 센다.
     */
    String CONTINUABLE_FOR_USER = """
        c.state = 'ACTIVE' AND n.mode = 'OPEN' AND n.status = 'ONGOING' AND n.is_public = TRUE
          AND n.owner_id <> :userId
          AND n.owner_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND c.author_id IS NOT NULL AND c.author_id <> :userId
          AND c.author_id NOT IN (SELECT u.id FROM users u WHERE u.status = 'TERMINATED')
          AND c.chapter_number < n.chapter_limit
          AND NOT EXISTS (SELECT 1 FROM novel_deliveries h WHERE h.novel_id = n.id AND h.recipient_id = :userId
                          AND h.status = 'DELIVERED' AND h.due_at > now())
          AND NOT EXISTS (SELECT 1 FROM novel_chapters m WHERE m.parent_chapter_id = c.id AND m.author_id = :userId
                          AND m.state IN ('ACTIVE', 'PRUNED'))
          AND n.branch_limit > (SELECT count(*) FROM novel_chapters k WHERE k.parent_chapter_id = c.id
                                  AND k.state IN ('ACTIVE', 'PRUNED') AND k.trunk = FALSE)
                             + (SELECT count(*) FROM novel_deliveries d WHERE d.chapter_id = c.id AND d.status = 'DELIVERED')
        """;

    Page<NovelChapter> findByNovelIdAndStateOrderByChapterNumberAscIdAsc(Long novelId, NovelChapterState state, Pageable pageable);
    /** 가지가 갈라진 작품은 같은 깊이에 회차가 여럿이라 먼저 쓴 회차를 준다. */
    Optional<NovelChapter> findFirstByNovelIdAndChapterNumberAndStateOrderByIdAsc(Long novelId, int chapterNumber, NovelChapterState state);
    /** 한 줄기 작품에서 마지막으로 쓴 회차 — 다음 회차의 부모가 된다. */
    Optional<NovelChapter> findFirstByNovelIdOrderByChapterNumberDescIdDesc(Long novelId);
    List<NovelChapter> findByNovelIdAndStateOrderByIdAsc(Long novelId, NovelChapterState state);
    Optional<NovelChapter> findByIdAndNovelId(Long id, Long novelId);
    @Query("""
        select count(distinct c.authorId) from NovelChapter c where c.novelId = :novelId and c.authorId is not null
        and c.state = 'ACTIVE'
        and c.authorId not in (select u.id from User u where u.status = 'TERMINATED')
        """)
    long countAuthors(@Param("novelId") Long novelId);

    /** 회차를 불러오지 않고 작품 id 만 — 작품을 먼저 잠근 뒤 회차를 새로 읽게 한다. */
    @Query("select c.novelId from NovelChapter c where c.id = :id")
    Optional<Long> novelIdOf(@Param("id") Long id);

    /** 이 회차에서 뻗은 회차 가운데 나무에 있거나 수거함에 있는 것(줄기 포함) — 끝 가지인지 볼 때 쓴다. */
    @Query("select count(c) from NovelChapter c where c.parentChapterId = :parentId and c.state in ('ACTIVE', 'PRUNED')")
    long countGrowingChildren(@Param("parentId") Long parentId);

    /** 이 회차의 가지 자리를 차지한 가지 — 나무에 있거나 수거함에 있는 가지. 첫 작가의 줄기는 자리와 따로 센다. */
    @Query("""
        select count(c) from NovelChapter c where c.parentChapterId = :parentId and c.state in ('ACTIVE', 'PRUNED')
        and c.trunk = false
        """)
    long countBranchChildren(@Param("parentId") Long parentId);

    /** 이 사람이 이 회차에서 이미 가지를 뻗었는가 — 한 회차에는 한 사람이 가지 하나만 뻗는다(지워진 가지는 세지 않는다). */
    default boolean hasBranchBy(Long parentId, Long authorId) {
        return existsByParentChapterIdAndAuthorIdAndStateIn(parentId, authorId,
                List.of(NovelChapterState.ACTIVE, NovelChapterState.PRUNED));
    }
    boolean existsByParentChapterIdAndAuthorIdAndStateIn(Long parentChapterId, Long authorId,
                                                         Collection<NovelChapterState> states);

    /** 첫 작가의 줄기 끝 회차. 다음 줄기 회차는 여기서 위로 이어진다. 줄기는 수거되지 않아 늘 나무에 있다. */
    Optional<NovelChapter> findFirstByNovelIdAndTrunkTrueOrderByChapterNumberDesc(Long novelId);

    /**
     * 홈 열매로 놓을 회차 고르기 — 이어 쓸 수 있는 회차 가운데 seenSince 뒤에 보여 준 것은 빼고,
     * 최근에 쓴 회차(하루 안 → 일주일 안 → 그 전) → 작품 장르가 내 관심 장르와 맞는 회차 순으로, 같은 묶음 안에서는 무작위로 고른다.
     */
    @Query(value = """
        SELECT c.id FROM novel_chapters c JOIN novels n ON n.id = c.novel_id
        WHERE """ + CONTINUABLE_FOR_USER + """
          AND NOT EXISTS (SELECT 1 FROM novel_home_seen s WHERE s.user_id = :userId AND s.chapter_id = c.id
                          AND s.shown_at > :seenSince)
        ORDER BY CASE WHEN c.created_at > :freshSince THEN 0 WHEN c.created_at > :recentSince THEN 1 ELSE 2 END,
                 EXISTS (SELECT 1 FROM novel_user_genres g WHERE g.user_id = :userId AND g.genre = ANY(n.genres)) DESC,
                 random()
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> pickHomeFruits(@Param("userId") Long userId, @Param("seenSince") Instant seenSince,
                              @Param("freshSince") Instant freshSince, @Param("recentSince") Instant recentSince,
                              @Param("limit") int limit);

    /** 주어진 회차 가운데 그 사람이 지금도 이어 쓸 수 있는 것. 순서는 부르는 쪽이 다시 맞춘다. */
    default List<Long> continuableAmong(Long userId, Collection<Long> chapterIds) {
        if (chapterIds.isEmpty()) return List.of();
        return continuableAmongNonEmpty(userId, chapterIds);
    }

    /** {@link #continuableAmong} 을 쓴다 — 빈 목록을 넘기면 IN 이 깨진다. */
    @Query(value = """
        SELECT c.id FROM novel_chapters c JOIN novels n ON n.id = c.novel_id
        WHERE c.id IN (:chapterIds) AND """ + CONTINUABLE_FOR_USER, nativeQuery = true)
    List<Long> continuableAmongNonEmpty(@Param("userId") Long userId, @Param("chapterIds") Collection<Long> chapterIds);

    /** 홈 열매로 보여 준 때를 남긴다. 다시 보여 주면 그때로 고친다. */
    @Modifying
    @Query(value = """
        INSERT INTO novel_home_seen (user_id, chapter_id, shown_at) VALUES (:userId, :chapterId, :shownAt)
        ON CONFLICT (user_id, chapter_id) DO UPDATE SET shown_at = EXCLUDED.shown_at
        """, nativeQuery = true)
    int markSeen(@Param("userId") Long userId, @Param("chapterId") Long chapterId, @Param("shownAt") Instant shownAt);

    /**
     * 수거할 끝 가지 후보 — 연재 중인 열매로 퍼뜨리는 작품의 뿌리가 아닌 회차 가운데, 판정 기간이 지났는데 조회가 모자라고
     * 나무에 있거나 수거함에 있는 자식 가지가 없는 것. 수거함의 자식이 지워지면 부모가 끝 가지가 되어 다음 판정에 걸린다.
     */
    @Query("""
        select c.id from NovelChapter c where c.state = 'ACTIVE' and c.parentChapterId is not null and c.trunk = false
        and c.judgedFrom <= :judgedBefore and c.viewCount < :minViews
        and c.novelId in (select n.id from Novel n where n.mode = 'OPEN' and n.status = 'ONGOING')
        and not exists (select k.id from NovelChapter k where k.parentChapterId = c.id and k.state in ('ACTIVE', 'PRUNED'))
        order by c.judgedFrom
        """)
    List<Long> staleTipIds(@Param("judgedBefore") Instant judgedBefore, @Param("minViews") int minViews, Pageable pageable);

    @Query("select c.id from NovelChapter c where c.state = 'PRUNED' and c.purgeAt <= :now order by c.purgeAt")
    List<Long> purgeDueIds(@Param("now") Instant now, Pageable pageable);

    /** 끝 가지인가 — 나무에 있거나 수거함에 있는 자식 가지가 없다. */
    default boolean isTip(Long chapterId) { return countGrowingChildren(chapterId) == 0; }

    /** 내 나뭇가지 수거함 — 지워질 날이 가까운 것부터. */
    @Query("select c from NovelChapter c where c.authorId = :authorId and c.state = 'PRUNED' order by c.purgeAt, c.id")
    List<NovelChapter> binOf(@Param("authorId") Long authorId);

    /** 처음 보는 사람이면 1, 이미 본 사람이면 0. */
    @Modifying
    @Query(value = "INSERT INTO novel_chapter_views (chapter_id, user_id) VALUES (:chapterId, :userId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int recordView(@Param("chapterId") Long chapterId, @Param("userId") Long userId);

    @Modifying
    @Query("update NovelChapter c set c.viewCount = c.viewCount + 1 where c.id = :id")
    int incrementViews(@Param("id") Long id);
}
