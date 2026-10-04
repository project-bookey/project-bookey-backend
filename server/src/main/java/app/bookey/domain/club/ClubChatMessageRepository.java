package app.bookey.domain.club;
import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.List;
/** 클럽 채팅 — 탈퇴한(계정이 종료된) 사람이 보낸 메시지는 목록에서도, 안 읽은 수에서도 뺀다. */
public interface ClubChatMessageRepository extends JpaRepository<ClubChatMessage,Long>{
 @Query("select m from ClubChatMessage m where m.clubId=:clubId and m.senderId not in (select t.id from User t where t.status='TERMINATED') order by m.id desc") List<ClubChatMessage> findAllByClubIdOrderByIdDesc(@Param("clubId") Long clubId,Pageable pageable);
 @Query("select m from ClubChatMessage m where m.clubId=:clubId and m.id<:beforeId and m.senderId not in (select t.id from User t where t.status='TERMINATED') order by m.id desc") List<ClubChatMessage> findAllByClubIdAndIdLessThanOrderByIdDesc(@Param("clubId") Long clubId,@Param("beforeId") Long beforeId,Pageable pageable);
 @Query("select count(m) from ClubChatMessage m where m.clubId=:clubId and m.senderId not in (select t.id from User t where t.status='TERMINATED')") long countByClubId(@Param("clubId") Long clubId);
 @Query("select count(m) from ClubChatMessage m where m.clubId=:clubId and m.id>:id and m.senderId not in (select t.id from User t where t.status='TERMINATED')") long countByClubIdAndIdGreaterThan(@Param("clubId") Long clubId,@Param("id") Long id);
 @Query("select max(m.id) from ClubChatMessage m where m.clubId=:clubId") Long lastId(@Param("clubId") Long clubId); }
