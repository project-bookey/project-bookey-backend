package app.bookey.domain.club;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {

    Optional<ClubMember> findByClubIdAndUserId(Long clubId, Long userId);

    List<ClubMember> findAllByClubIdAndStatus(Long clubId, ClubMemberStatus status);

    List<ClubMember> findAllByClubIdInAndUserIdAndStatus(List<Long> clubIds, Long userId,
                                                         ClubMemberStatus status);

    long countByClubIdAndStatus(Long clubId, ClubMemberStatus status);

    @Query("""
            SELECT m FROM ClubMember m
            WHERE m.userId = :userId AND m.status = 'ACTIVE'
            ORDER BY m.joinedAt DESC
            """)
    Page<ClubMember> findMyClubs(@Param("userId") Long userId, Pageable pageable);

    List<ClubMember> findAllByUserIdAndStatus(Long userId, ClubMemberStatus status);

    /** 특정 독서 기록에 연결된 모임 멤버십 — 세션 종료 시 모임 진척 동기화용. */
    List<ClubMember> findAllByReadingRecordIdAndStatus(Long readingRecordId, ClubMemberStatus status);
}
