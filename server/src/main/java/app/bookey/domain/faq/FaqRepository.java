package app.bookey.domain.faq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByVisibleTrueOrderBySortOrderAscIdAsc();

    List<Faq> findAllByOrderBySortOrderAscIdAsc();

    /** 새 FAQ 를 맨 뒤에 붙이는 데 쓴다 — 비어 있으면 -1. */
    @Query("SELECT COALESCE(MAX(f.sortOrder), -1) FROM Faq f")
    int maxSortOrder();
}
