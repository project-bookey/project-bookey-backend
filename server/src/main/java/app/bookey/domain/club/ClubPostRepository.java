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
              AND p.type <> 'LOG'
            ORDER BY p.pinned DESC, p.createdAt DESC
            """)
    Page<ClubPost> findFeed(@Param("clubId") Long clubId, Pageable pageable);

    /** "내 진도까지만 보기" — 앵커가 없는 글과 내 진도 이하의 글만 가져온다. */
    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.parentId IS NULL
              AND p.status = 'VISIBLE'
              AND p.type <> 'LOG'
              AND (p.anchorPage IS NULL OR p.anchorPage <= :maxAnchorPage)
            ORDER BY p.pinned DESC, p.createdAt DESC
            """)
    Page<ClubPost> findFeedUpTo(@Param("clubId") Long clubId,
                                @Param("maxAnchorPage") int maxAnchorPage,
                                Pageable pageable);

    /** 읽기로그 보드 — 기간 안의 조각, 오래된 순. */
    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.type = 'LOG'
              AND p.status = 'VISIBLE'
              AND p.createdAt >= :from AND p.createdAt < :to
            ORDER BY p.createdAt ASC
            """)
    List<ClubPost> findLogs(@Param("clubId") Long clubId, @Param("from") Instant from, @Param("to") Instant to);

    /** 요일 스트립 — 기간 안 조각의 작성 시각만. 날짜 묶기는 KST 로 서비스가 한다. */
    @Query("""
            SELECT p.createdAt FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.type = 'LOG'
              AND p.status = 'VISIBLE'
              AND p.createdAt >= :from AND p.createdAt < :to
            """)
    List<Instant> findLogTimes(@Param("clubId") Long clubId, @Param("from") Instant from, @Param("to") Instant to);

    /** 주간 카드 '가장 많이 멈춘 문장' 후보 — 기간 안의 인용 글, 반응 많은 순. */
    @Query("""
            SELECT p FROM ClubPost p
            WHERE p.clubId = :clubId
              AND p.type = 'QUOTE'
              AND p.parentId IS NULL
              AND p.status = 'VISIBLE'
              AND p.createdAt >= :from AND p.createdAt < :to
            ORDER BY p.reactionCount DESC, p.createdAt ASC
            """)
    List<ClubPost> findQuotesBetween(@Param("clubId") Long clubId, @Param("from") Instant from, @Param("to") Instant to);

    /** 주간 알림 대상 — 기간 안에 조각이 하나라도 있는 모임. */
    @Query("""
            SELECT DISTINCT p.clubId FROM ClubPost p
            WHERE p.type = 'LOG' AND p.status = 'VISIBLE'
              AND p.createdAt >= :from AND p.createdAt < :to
            """)
    List<Long> findClubIdsWithLogsBetween(@Param("from") Instant from, @Param("to") Instant to);

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
