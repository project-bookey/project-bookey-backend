package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NovelBaseWorkRepository extends JpaRepository<NovelBaseWork, Long> {
    List<NovelBaseWork> findByActiveTrueOrderBySortOrderAscIdAsc();
}
