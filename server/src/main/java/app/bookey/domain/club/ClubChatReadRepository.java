package app.bookey.domain.club; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Modifying; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; public interface ClubChatReadRepository extends JpaRepository<ClubChatRead,ClubChatRead.Key>{
 /** 나간 멤버의 읽음 위치 — 다시 참가하면 새 멤버처럼 처음부터 센다. */
 @Modifying @Query("delete from ClubChatRead r where r.clubId=:clubId and r.userId=:userId") void deleteByClubIdAndUserId(@Param("clubId") Long clubId,@Param("userId") Long userId); }
