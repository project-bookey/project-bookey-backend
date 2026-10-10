package app.bookey.domain.novel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface NovelCoverRepository extends JpaRepository<NovelCover, Long> {
    @Query("select c.id from NovelCover c where c.novelId is null and c.updatedAt < :cutoff")
    List<Long> orphanIds(@Param("cutoff") Instant cutoff, Pageable pageable);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NovelCover c where c.id = :id")
    Optional<NovelCover> lockById(@Param("id") Long id);
}
