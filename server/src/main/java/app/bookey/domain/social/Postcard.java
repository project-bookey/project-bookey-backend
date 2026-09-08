package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 엽서 (§14.2) — 피드에서 스친 사람에게 보내는 유일한 어필 수단.
 * 본문은 16글자(grapheme). 답장이 성립하면 두 사람은 자동 맞팔로우된다.
 */
@Getter
@Entity
@Table(name = "postcards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Postcard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    /** 어떤 독후감을 보고 보냈나 — 수신함에 컨텍스트로 표시. */
    @Column(name = "post_id")
    private Long postId;

    @Column(nullable = false, length = 128)
    private String body;

    /** 우표 동봉 — 발신자가 우표를 부담해 수신자가 무료로 답장할 수 있다. */
    @Column(name = "stamp_attached", nullable = false)
    private boolean stampAttached;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostcardStatus status;

    @Column(name = "reply_body", length = 128)
    private String replyBody;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Builder
    private Postcard(Long fromUserId, Long toUserId, Long postId, String body, boolean stampAttached) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.postId = postId;
        this.body = body;
        this.stampAttached = stampAttached;
        this.status = PostcardStatus.SENT;
    }

    public boolean isRecipient(Long userId) {
        return toUserId.equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return fromUserId.equals(userId) || toUserId.equals(userId);
    }

    public boolean isReplied() {
        return status == PostcardStatus.REPLIED;
    }

    public void reply(String body, Instant now) {
        this.replyBody = body;
        this.repliedAt = now;
        this.status = PostcardStatus.REPLIED;
    }
}
