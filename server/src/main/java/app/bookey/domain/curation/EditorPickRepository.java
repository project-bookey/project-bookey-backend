package app.bookey.domain.curation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditorPickRepository extends JpaRepository<EditorPick, Long> {
    List<EditorPick> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByBookId(Long bookId);
}
