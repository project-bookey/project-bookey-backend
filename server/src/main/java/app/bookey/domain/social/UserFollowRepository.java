package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    Optional<UserFollow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /** 팔로워 수 — 탈퇴한(계정이 종료된) 사람은 빼고 센다. 아래 목록·수도 모두 같다. */
    @Query("""
            SELECT COUNT(f) FROM UserFollow f
            WHERE f.followeeId = :followeeId
              AND f.followerId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            """)
    long countByFolloweeId(@Param("followeeId") Long followeeId);

    @Query("""
            SELECT COUNT(f) FROM UserFollow f
            WHERE f.followerId = :followerId
              AND f.followeeId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            """)
    long countByFollowerId(@Param("followerId") Long followerId);

    /** 나를 팔로우하는 사람들 — 최신순. */
    @Query("""
            SELECT f FROM UserFollow f
            WHERE f.followeeId = :followeeId
              AND f.followerId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY f.id DESC
            """)
    Page<UserFollow> findAllByFolloweeIdOrderByIdDesc(@Param("followeeId") Long followeeId, Pageable pageable);

    /** 내가 팔로우하는 사람들 — 최신순. */
    @Query("""
            SELECT f FROM UserFollow f
            WHERE f.followerId = :followerId
              AND f.followeeId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY f.id DESC
            """)
    Page<UserFollow> findAllByFollowerIdOrderByIdDesc(@Param("followerId") Long followerId, Pageable pageable);

    /** 맞팔로우 플래그 배치 판정용 — 내가 이 사람들을 팔로우하는가. */
    List<UserFollow> findAllByFollowerIdAndFolloweeIdIn(Long followerId, Collection<Long> followeeIds);

    /** 맞팔로우 플래그 배치 판정용 — 이 사람들이 나를 팔로우하는가. */
    List<UserFollow> findAllByFolloweeIdAndFollowerIdIn(Long followeeId, Collection<Long> followerIds);

    /** 내가 팔로우하는 사람 id 전부 — 팔로우 버튼 상태 판정용. */
    @Query("SELECT f.followeeId FROM UserFollow f WHERE f.followerId = :followerId")
    List<Long> findFolloweeIdsByFollowerId(@Param("followerId") Long followerId);
}
