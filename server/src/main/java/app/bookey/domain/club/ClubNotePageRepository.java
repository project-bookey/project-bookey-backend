package app.bookey.domain.club;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClubNotePageRepository extends JpaRepository<ClubNotePage, Long> {

    /** 목록은 문서 없이 요약만 — (club_id, seq) 유니크 인덱스를 그대로 탄다. */
    @Query("""
            SELECT new app.bookey.domain.club.ClubNotePageSummary(
                p.id, p.seq, p.title, p.version, p.elementCount, p.updatedBy, p.updatedAt)
            FROM ClubNotePage p
            WHERE p.clubId = :clubId
            ORDER BY p.seq ASC
            """)
    List<ClubNotePageSummary> findSummaries(@Param("clubId") Long clubId);

    /** 저장용 — 버전 비교부터 덮어쓰기까지를 한 행에서 직렬화한다(같은 페이지의 동시 저장은 둘 중 하나가 409). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ClubNotePage p WHERE p.id = :id")
    Optional<ClubNotePage> findByIdForUpdate(@Param("id") Long id);

    long countByClubId(Long clubId);

    /** 다음 seq 계산용 — 페이지가 없으면 0. */
    @Query("SELECT COALESCE(MAX(p.seq), 0) FROM ClubNotePage p WHERE p.clubId = :clubId")
    Integer findMaxSeq(@Param("clubId") Long clubId);
}
