package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NovelDraftRepository extends JpaRepository<NovelDraft, Long> {
    Optional<NovelDraft> findByNovelIdAndUserId(Long novelId, Long userId);
}
