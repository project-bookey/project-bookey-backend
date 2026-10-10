package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface NovelChapterRepository extends JpaRepository<NovelChapter, Long> {
    Page<NovelChapter> findByNovelIdOrderByChapterNumberAsc(Long novelId, Pageable pageable);
    Optional<NovelChapter> findByNovelIdAndChapterNumber(Long novelId, int chapterNumber);
}
