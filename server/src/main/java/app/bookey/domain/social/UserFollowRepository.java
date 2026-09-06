package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    Optional<UserFollow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    /** 나를 팔로우하는 사람들 — 최신순. */
    Page<UserFollow> findAllByFolloweeIdOrderByIdDesc(Long followeeId, Pageable pageable);

    /** 내가 팔로우하는 사람들 — 최신순. */
    Page<UserFollow> findAllByFollowerIdOrderByIdDesc(Long followerId, Pageable pageable);

    /** 맞팔로우 플래그 배치 판정용 — 내가 이 사람들을 팔로우하는가. */
    List<UserFollow> findAllByFollowerIdAndFolloweeIdIn(Long followerId, Collection<Long> followeeIds);

    /** 맞팔로우 플래그 배치 판정용 — 이 사람들이 나를 팔로우하는가. */
    List<UserFollow> findAllByFolloweeIdAndFollowerIdIn(Long followeeId, Collection<Long> followerIds);
}
