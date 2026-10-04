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

/**
 * 독서 모임 (§F12). 기간 없이 이어지고 책 한 권에 묶이지 않는다 — 책은 만남마다 고르고,
 * 다가오는 만남의 책이 '지금 읽는 책'({@link #currentClubBookId})이 된다({@link ClubCurrentBook}).
 */
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

    /** 클럽 머리 배경 사진 — 호스트가 올린다. 없으면 종이 바탕. */
    @Column(name = "background_url", columnDefinition = "text")
    private String backgroundUrl;

    /** 배경 사진의 저장소 키 — 바꾸거나 뺄 때 이전 파일을 지우려고 둔다. */
    @Column(name = "background_key", length = 255)
    private String backgroundKey;

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

    /** 모임을 연 날. */
    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    /** 기간이 있던 예전 모임만 값이 있다 — 이제 모임은 기간 없이 이어지고 호스트가 끝낼 때 끝난다. */
    @Column(name = "ends_at")
    private LocalDate endsAt;

    /** 지금 읽는 책 — club_books 한 줄. 책을 고른 만남이 아직 없으면 null. */
    @Column(name = "current_club_book_id")
    private Long currentClubBookId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "allow_nudge", nullable = false)
    private boolean allowNudge = true;

    @Builder
    private Club(Long ownerId, String name, String description, String coverUrl, String joinCode,
                 ClubVisibility visibility, short memberLimit, LocalDate startsAt, boolean allowNudge) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.joinCode = joinCode;
        this.visibility = visibility == null ? ClubVisibility.CODE_ONLY : visibility;
        this.memberLimit = memberLimit;
        this.memberCount = 0;
        this.startsAt = startsAt;
        this.allowNudge = allowNudge;
        this.status = ClubStatus.RECRUITING;
    }

    /** 배경 사진을 바꾸고(null 이면 뺀다) 이전 사진의 저장소 키를 돌려준다 — 호출자가 파일을 지운다. */
    public String changeBackground(String url, String key) {
        String previous = this.backgroundKey;
        this.backgroundUrl = url;
        this.backgroundKey = key;
        return previous;
    }

    public void changeCurrentBook(Long clubBookId) {
        this.currentClubBookId = clubBookId;
    }

    public void rotateJoinCode(String newCode) {
        this.joinCode = newCode;
    }

    /** 정원은 여기서 바꾸지 않는다 — 올리기는 책갈피 결제({@link #expandMemberLimit}), 내리기는 산 자리를 버리는 경로라 막는다. */
    public void update(String name, String description, ClubVisibility visibility, Boolean allowNudge) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (visibility != null) {
            this.visibility = visibility;
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

    /**
     * 정원을 targetLimit 으로 늘리고 새로 연 자리 수를 돌려준다. 목표 정원은 step 의 배수만 받는다.
     * 호스트 검사와 책갈피 결제는 서비스가 맡는다. 늘린 자리는 이 모임에만 속하고 종료 후에도 되돌리지 않는다
     * — 종료된 모임은 참가가 막히므로 자리가 자연히 사라지고, 결산 화면은 최종 정원을 그대로 보여준다.
     */
    public int expandMemberLimit(int targetLimit, int maxLimit, int step) {
        if (status.isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
        if (targetLimit <= memberLimit) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지금 정원보다 많은 인원을 골라 주세요.");
        }
        if (targetLimit > maxLimit) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "정원은 " + maxLimit + "명까지 늘릴 수 있어요.");
        }
        if (targetLimit % step != 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "정원은 " + step + "명 단위로 늘릴 수 있어요.");
        }
        int added = targetLimit - memberLimit;
        this.memberLimit = (short) targetLimit;
        return added;
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

    /** 기간이 있던 예전 모임의 남은 날 — 기간 없는 모임은 0. 예전 앱이 읽는 daysLeft 를 채우려고만 남겨 둔다. */
    public long daysLeft(LocalDate today) {
        return endsAt == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(today, endsAt);
    }
}
