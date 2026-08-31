package app.bookey.domain.club;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClubPostRepository extends JpaRepository<ClubPost, Long> {

    /**
     * 토론 목록. 스포일러 마스킹은 서버에서 본문을 제거하는 방식이므로
     * 조회 자체는 전체를 가져오되, "내 진도까지만 보기"가 켜지면 anchor 필터를 건다(§8.5).
     */
    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.parentId IS NULL
              AND p.status = 'VISIBLE'
            ORDER BY p.pinned DESC, p.createdAt DESC
            """)
    Page<ClubPost> findFeed(@Param("clubId") Long clubId, Pageable pageable);

    /** "내 진도까지만 보기" — 앵커가 없는 글과 내 진도 이하의 글만 가져온다. */
    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.parentId IS NULL
              AND p.status = 'VISIBLE'
              AND (p.anchorPage IS NULL OR p.anchorPage <= :maxAnchorPage)
            ORDER BY p.pinned DESC, p.createdAt DESC
            """)
    Page<ClubPost> findFeedUpTo(@Param("clubId") Long clubId,
                                @Param("maxAnchorPage") int maxAnchorPage,
                                Pageable pageable);

    List<ClubPost> findAllByParentIdAndStatusOrderByCreatedAtAsc(Long parentId, String status);

    List<ClubPost> findAllByParentIdInAndStatus(List<Long> parentIds, String status);

    long countByClubIdAndStatus(Long clubId, String status);

    long countByClubIdAndUserIdAndStatus(Long clubId, Long userId, String status);

    /** 도배 탐지 — 1분 내 작성 수(§8.5 모임 어뷰징). */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId AND p.status = 'VISIBLE' AND p.type = 'QUOTE'
            ORDER BY p.reactionCount DESC
            """)
    List<ClubPost> findBestQuotes(@Param("clubId") Long clubId, Pageable pageable);
}
