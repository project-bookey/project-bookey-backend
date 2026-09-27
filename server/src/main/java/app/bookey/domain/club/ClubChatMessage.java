package app.bookey.domain.club;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Getter @Entity @Table(name="club_chat_messages") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ClubChatMessage { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="club_id",nullable=false) private Long clubId; @Column(name="sender_id",nullable=false) private Long senderId; @Column(nullable=false,length=1000) private String body; @Column(name="created_at",insertable=false,updatable=false) private Instant createdAt=Instant.now(); public ClubChatMessage(Long clubId,Long senderId,String body){this.clubId=clubId;this.senderId=senderId;this.body=body;} }
