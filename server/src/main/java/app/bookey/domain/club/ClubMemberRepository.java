package app.bookey.domain.club;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {

    Optional<ClubMember> findByClubIdAndUserId(Long clubId, Long userId);

    List<ClubMember> findAllByClubIdAndStatus(Long clubId, ClubMemberStatus status);

    /** 채팅 보낸 사람들의 멤버 행 — 나간 사람·다시 참가한 사람의 예전 메시지를 가려낸다. 상태를 가리지 않는다. */
    List<ClubMember> findAllByClubIdAndUserIdIn(Long clubId, Collection<Long> userIds);

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

    /** 관리자 모임 상세 — 나간·내보내진 멤버까지 모두. */
    List<ClubMember> findAllByClubIdOrderByJoinedAtAsc(Long clubId);
}
