package app.bookey.domain.club;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** 독서 모임 (§F12). */
@Getter
@Entity
@Table(name = "clubs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Club extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "cover_url")
    private String coverUrl;

    /** 6자 base32 초대 코드. 회전 가능(§8.5). */
    @Column(name = "join_code", nullable = false, unique = true, length = 6)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClubVisibility visibility = ClubVisibility.CODE_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClubStatus status = ClubStatus.RECRUITING;

    @Column(name = "member_limit", nullable = false)
    private short memberLimit;

    @Column(name = "member_count", nullable = false)
    private short memberCount;

    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDate endsAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "allow_nudge", nullable = false)
    private boolean allowNudge = true;

    @Builder
    private Club(Long ownerId, String name, String description, String coverUrl, String joinCode,
                 ClubVisibility visibility, short memberLimit, LocalDate startsAt, LocalDate endsAt,
                 boolean allowNudge) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.joinCode = joinCode;
        this.visibility = visibility == null ? ClubVisibility.CODE_ONLY : visibility;
        this.memberLimit = memberLimit;
        this.memberCount = 0;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allowNudge = allowNudge;
        this.status = ClubStatus.RECRUITING;
    }

    public void rotateJoinCode(String newCode) {
        this.joinCode = newCode;
    }

    public void update(String name, String description, ClubVisibility visibility,
                       Short memberLimit, LocalDate endsAt, Boolean allowNudge) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
        if (memberLimit != null) {
            if (memberLimit < this.memberCount) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "현재 인원보다 적게 줄일 수 없습니다.");
            }
            this.memberLimit = memberLimit;
        }
        if (endsAt != null) {
            if (endsAt.isBefore(this.startsAt)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "종료일이 시작일보다 빠릅니다.");
            }
            this.endsAt = endsAt;
        }
        if (allowNudge != null) {
            this.allowNudge = allowNudge;
        }
    }

    public void joinMember() {
        if (status.isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
        if (memberCount >= memberLimit) {
            throw ApiException.of(ErrorCode.CLUB_FULL);
        }
        this.memberCount++;
        if (this.status == ClubStatus.RECRUITING && this.memberCount >= 2) {
            this.status = ClubStatus.ACTIVE;
        }
    }

    public void leaveMember() {
        if (this.memberCount > 0) {
            this.memberCount--;
        }
    }

    public void end() {
        this.status = ClubStatus.ENDED;
        this.endedAt = Instant.now();
    }

    public void archive() {
        this.status = ClubStatus.ARCHIVED;
    }

    public void transferHost(Long newOwnerId) {
        this.ownerId = newOwnerId;
    }

    public boolean isHost(Long userId) {
        return ownerId.equals(userId);
    }

    public boolean isFull() {
        return memberCount >= memberLimit;
    }

    public long daysLeft(LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, endsAt);
    }
}
