package app.bookey.domain.club; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.time.Instant; import java.util.List; import java.util.Optional;
public interface ClubMeetingRepository extends JpaRepository<ClubMeeting,Long>{List<ClubMeeting> findAllByClubIdOrderByStartsAtAsc(Long clubId);
 /** 다음 만남 — 목록 카드·홈 머리의 '다음 모임'. status 는 'OPEN'. */
 Optional<ClubMeeting> findFirstByClubIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(Long clubId,String status,Instant after);
 /** 참여 — 정원 검사 전에 모임 행을 잠근다(동시 참여가 정원을 넘지 않게). */
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT m FROM ClubMeeting m WHERE m.id = :id") Optional<ClubMeeting> findByIdForUpdate(@Param("id") Long id);}
