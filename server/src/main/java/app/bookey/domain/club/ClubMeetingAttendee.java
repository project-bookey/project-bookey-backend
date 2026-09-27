package app.bookey.domain.club;
import jakarta.persistence.*; import lombok.*; import java.io.Serializable; import java.time.Instant;
@Getter @Entity @Table(name="club_meeting_attendees") @IdClass(ClubMeetingAttendee.Key.class) @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ClubMeetingAttendee { @Id @Column(name="meeting_id") private Long meetingId; @Id @Column(name="user_id") private Long userId; @Column(name="responded_at",nullable=false) private Instant respondedAt=Instant.now(); public ClubMeetingAttendee(Long m,Long u){meetingId=m;userId=u;} @Data @NoArgsConstructor @AllArgsConstructor public static class Key implements Serializable{private Long meetingId;private Long userId;} }
