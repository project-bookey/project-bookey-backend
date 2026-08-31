package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 찌르기 기록. 공개 피드에 남지 않는다(§12.4). */
@Getter
@Entity
@Table(name = "club_nudges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubNudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_key", nullable = false, length = 30)
    private NudgeMessage messageKey;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ClubNudge(Long clubId, Long fromUserId, Long toUserId, NudgeMessage messageKey) {
        this.clubId = clubId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.messageKey = messageKey;
    }
}
