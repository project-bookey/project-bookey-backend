package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 읽는 쪽 조회는 나무에 있는(ACTIVE) 회차만 준다. 수거함의 가지는 글쓴이의 수거함에서만 보인다. */
public interface NovelChapterRepository extends JpaRepository<NovelChapter, Long> {
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

    /** 이 회차에서 뻗은 가지 가운데 자리를 차지하는 것 — 나무에 있거나 수거함에 있는 가지. */
    @Query("select count(c) from NovelChapter c where c.parentChapterId = :parentId and c.state in ('ACTIVE', 'PRUNED')")
    long countGrowingChildren(@Param("parentId") Long parentId);

    /**
     * 수거할 끝 가지 후보 — 연재 중인 열매로 퍼뜨리는 작품의 뿌리가 아닌 회차 가운데, 판정 기간이 지났는데 조회가 모자라고
     * 나무에 있거나 수거함에 있는 자식 가지가 없는 것. 수거함의 자식이 지워지면 부모가 끝 가지가 되어 다음 판정에 걸린다.
     */
    @Query("""
        select c.id from NovelChapter c where c.state = 'ACTIVE' and c.parentChapterId is not null
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
