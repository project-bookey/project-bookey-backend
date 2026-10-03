package app.bookey.domain.club; import org.springframework.data.jpa.repository.JpaRepository; import java.time.Instant; import java.util.List; import java.util.Optional;
public interface ClubMeetingRepository extends JpaRepository<ClubMeeting,Long>{List<ClubMeeting> findAllByClubIdOrderByStartsAtAsc(Long clubId);
 /** 다음 만남 — 목록 카드·홈 머리의 '다음 모임'. status 는 'OPEN'. */
 Optional<ClubMeeting> findFirstByClubIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(Long clubId,String status,Instant after);}
