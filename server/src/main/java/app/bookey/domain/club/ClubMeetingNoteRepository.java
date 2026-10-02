package app.bookey.domain.club;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClubMeetingNoteRepository extends JpaRepository<ClubMeetingNote, Long> {

    Optional<ClubMeetingNote> findByMeetingId(Long meetingId);

    /** 실시간 연결 인증 때 문서 없이 version 만. */
    @Query("SELECT n.version FROM ClubMeetingNote n WHERE n.meetingId = :meetingId")
    Optional<Integer> findVersionByMeetingId(@Param("meetingId") Long meetingId);

    /** 연산 적용용 — 같은 노트에 동시에 들어온 연산을 한 줄로 세운다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM ClubMeetingNote n WHERE n.meetingId = :meetingId")
    Optional<ClubMeetingNote> findByMeetingIdForUpdate(@Param("meetingId") Long meetingId);

    /**
     * 노트가 없으면 빈 노트를 만든다 — 두 멤버가 동시에 첫 편집을 해도 meeting_id 유니크에 걸려 하나만 생긴다.
     * 엔티티 save 로 만들면 진 쪽이 유니크 위반으로 트랜잭션째 실패하므로 ON CONFLICT 로 조용히 넘긴다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO club_meeting_notes (club_id, meeting_id, document)
            VALUES (:clubId, :meetingId, '{}'::jsonb)
            ON CONFLICT (meeting_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("clubId") Long clubId, @Param("meetingId") Long meetingId);

    /** 피드 — 빈 노트는 빼고 최근에 고친 순. */
    Page<ClubMeetingNote> findAllByClubIdAndElementCountGreaterThanOrderByUpdatedAtDesc(
            Long clubId, int elementCount, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO club_meeting_note_contributors (note_id, user_id)
            VALUES (:noteId, :userId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addContributor(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /** [note_id, user_id] 쌍 — 처음 손댄 순. */
    @Query(value = """
            SELECT note_id, user_id FROM club_meeting_note_contributors
            WHERE note_id IN (:noteIds)
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<Object[]> findContributorPairs(@Param("noteIds") Collection<Long> noteIds);
}
