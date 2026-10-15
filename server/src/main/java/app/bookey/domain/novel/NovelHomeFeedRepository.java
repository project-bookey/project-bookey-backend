package app.bookey.domain.novel;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface NovelHomeFeedRepository extends JpaRepository<NovelHomeFeed, Long> {
    /** 조회·새로고침 모두 행을 잠근다 — 연달아 눌러도 하루 무료 횟수를 넘기지 않고, 빈 묶음을 채울 때 새로고침을 덮어쓰지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from NovelHomeFeed f where f.userId = :userId")
    Optional<NovelHomeFeed> lockByUserId(@Param("userId") Long userId);

    /**
     * 행이 없으면 빈 묶음으로 만든다. 처음 요청 둘이 겹쳐도 user_id 유니크에 걸려 하나만 생긴다 —
     * 엔티티 save 로 만들면 진 쪽이 유니크 위반으로 트랜잭션째 실패하므로 ON CONFLICT 로 조용히 넘긴다.
     * 만들었으면 1, 이미 있었으면 0 이다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO novel_home_feeds (user_id, free_reset_date) VALUES (:userId, :today)
        ON CONFLICT (user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("today") LocalDate today);
}
