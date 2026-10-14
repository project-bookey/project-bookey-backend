package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.List;
import java.util.Optional;

public interface NovelChapterRepository extends JpaRepository<NovelChapter, Long> {
    Page<NovelChapter> findByNovelIdOrderByChapterNumberAscIdAsc(Long novelId, Pageable pageable);
    /** 가지가 갈라진 작품은 같은 깊이에 회차가 여럿이라 먼저 쓴 회차를 준다. */
    Optional<NovelChapter> findFirstByNovelIdAndChapterNumberOrderByIdAsc(Long novelId, int chapterNumber);
    /** 한 줄기 작품에서 마지막으로 쓴 회차 — 다음 회차의 부모가 된다. */
    Optional<NovelChapter> findFirstByNovelIdOrderByChapterNumberDescIdDesc(Long novelId);
    List<NovelChapter> findByNovelIdOrderByIdAsc(Long novelId);
    Optional<NovelChapter> findByIdAndNovelId(Long id, Long novelId);
    @Query("""
        select count(distinct c.authorId) from NovelChapter c where c.novelId = :novelId and c.authorId is not null
        and c.authorId not in (select u.id from User u where u.status = 'TERMINATED')
        """)
    long countAuthors(@Param("novelId") Long novelId);
}
