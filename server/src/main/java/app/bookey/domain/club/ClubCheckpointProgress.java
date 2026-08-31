package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 체크포인트 마감 시점 스냅샷. 사후에 진도를 올려도 결과는 바뀌지 않는다. */
@Getter
@Entity
@Table(name = "club_checkpoint_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubCheckpointProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkpoint_id", nullable = false)
    private Long checkpointId;

    @Column(name = "club_member_id", nullable = false)
    private Long clubMemberId;

    @Column(name = "page_at_due", nullable = false)
    private int pageAtDue;

    @Column(nullable = false)
    private boolean achieved;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt = Instant.now();

    public ClubCheckpointProgress(Long checkpointId, Long clubMemberId, int pageAtDue, boolean achieved) {
        this.checkpointId = checkpointId;
        this.clubMemberId = clubMemberId;
        this.pageAtDue = pageAtDue;
        this.achieved = achieved;
        this.achievedAt = achieved ? Instant.now() : null;
        this.evaluatedAt = Instant.now();
    }
}
