package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NovelUserGenreRepository extends JpaRepository<NovelUserGenre, Long> {
    List<NovelUserGenre> findByUserIdOrderByIdAsc(Long userId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NovelUserGenre g where g.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
