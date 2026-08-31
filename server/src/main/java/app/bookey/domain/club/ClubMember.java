package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "club_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 개인 독서 기록과 연결 — 모임 진척은 개인 진척을 그대로 재사용한다(§12.2). */
    @Column(name = "reading_record_id")
    private Long readingRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClubRole role = ClubRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClubMemberStatus status = ClubMemberStatus.ACTIVE;

    /** 진척 공개 여부. 끄면 리더보드에서 "비공개" 처리되고 모임 통계에서 제외된다. */
    @Column(name = "share_progress", nullable = false)
    private boolean shareProgress = true;

    @Column(name = "allow_nudge", nullable = false)
    private boolean allowNudge = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "kick_reason", length = 200)
    private String kickReason;

    @Builder
    private ClubMember(Long clubId, Long userId, Long readingRecordId, ClubRole role,
                       boolean shareProgress, boolean allowNudge) {
        this.clubId = clubId;
        this.userId = userId;
        this.readingRecordId = readingRecordId;
        this.role = role == null ? ClubRole.MEMBER : role;
        this.status = ClubMemberStatus.ACTIVE;
        this.shareProgress = shareProgress;
        this.allowNudge = allowNudge;
        this.joinedAt = Instant.now();
    }

    public void rejoin(Long readingRecordId) {
        this.status = ClubMemberStatus.ACTIVE;
        this.readingRecordId = readingRecordId;
        this.leftAt = null;
        this.joinedAt = Instant.now();
    }

    public void leave() {
        this.status = ClubMemberStatus.LEFT;
        this.leftAt = Instant.now();
    }

    public void kick(String reason) {
        this.status = ClubMemberStatus.KICKED;
        this.leftAt = Instant.now();
        this.kickReason = reason;
    }

    public void changeRole(ClubRole role) {
        this.role = role;
    }

    public void updateSharing(Boolean shareProgress, Boolean allowNudge) {
        if (shareProgress != null) {
            this.shareProgress = shareProgress;
        }
        if (allowNudge != null) {
            this.allowNudge = allowNudge;
        }
    }

    public void touchLastRead(Instant at) {
        this.lastReadAt = at;
    }

    public void linkReadingRecord(Long readingRecordId) {
        this.readingRecordId = readingRecordId;
    }

    public boolean isActive() {
        return status == ClubMemberStatus.ACTIVE;
    }

    public boolean canModerate() {
        return isActive() && role.canModerate();
    }
}
