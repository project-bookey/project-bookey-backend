package app.bookey.domain.club;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Getter @Entity @Table(name="club_activity_sessions") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ClubActivitySession { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="club_id",nullable=false) private Long clubId; @Column(name="meeting_id") private Long meetingId; @Column(name="user_id",nullable=false) private Long userId; @Column(name="started_at",nullable=false) private Instant startedAt=Instant.now(); @Column(name="ended_at") private Instant endedAt; @Column(name="duration_sec") private Integer durationSec; public ClubActivitySession(Long c,Long meeting,Long u){clubId=c;meetingId=meeting;userId=u;}
 /** 같이 읽은 시간 상한 — 독서 타이머(ReadingSession.MAX_SESSION)와 같은 4시간. 넘겨서 끝내면 4시간째에 끝난 것으로 본다. */
 public static final Duration MAX_DURATION=Duration.ofHours(4);
 public void end(Instant now){Duration d=Duration.between(startedAt,now);if(d.compareTo(MAX_DURATION)>0){d=MAX_DURATION;now=startedAt.plus(MAX_DURATION);}endedAt=now;durationSec=(int)Math.max(1,d.getSeconds());} }
