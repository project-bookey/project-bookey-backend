package app.bookey.domain.club;
import jakarta.persistence.*; import lombok.*;
/** 함께 독서 기록 카드 — 스탑워치를 끝낼 때 세션마다 하나. 꾸미기는 없고, 독후감 노트의 스티커로 붙여 쓴다. */
@Getter @Entity @Table(name="club_activity_cards") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ClubActivityCard { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="club_id",nullable=false) private Long clubId; @Column(name="session_id",nullable=false) private Long sessionId; @Column(name="user_id",nullable=false) private Long userId; public ClubActivityCard(Long c,Long s,Long u){clubId=c;sessionId=s;userId=u;} }
