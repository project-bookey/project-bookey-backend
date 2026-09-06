package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1:1 채팅방 (§14.3) — 맞팔로우끼리만. 사용자 쌍당 1개.
 * aUserId < bUserId 로 정규화해 (나→상대)와 (상대→나)가 같은 방을 얻는다.
 */
@Getter
@Entity
@Table(name = "chats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "a_user_id", nullable = false)
    private Long aUserId;

    @Column(name = "b_user_id", nullable = false)
    private Long bUserId;

    @Column(name = "a_last_read_at")
    private Instant aLastReadAt;

    @Column(name = "b_last_read_at")
    private Instant bLastReadAt;

    /** 목록 정렬용 — 메시지가 없으면 createdAt 을 쓴다. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** 정규화된 쌍으로 만든다 — 순서가 뒤집혀 들어와도 같은 방. */
    public static Chat of(Long userId, Long otherUserId) {
        Chat chat = new Chat();
        chat.aUserId = Math.min(userId, otherUserId);
        chat.bUserId = Math.max(userId, otherUserId);
        return chat;
    }

    public boolean isParticipant(Long userId) {
        return aUserId.equals(userId) || bUserId.equals(userId);
    }

    public Long counterpartOf(Long userId) {
        return aUserId.equals(userId) ? bUserId : aUserId;
    }

    public Instant lastReadOf(Long userId) {
        return aUserId.equals(userId) ? aLastReadAt : bLastReadAt;
    }

    public void markRead(Long userId, Instant now) {
        if (aUserId.equals(userId)) {
            this.aLastReadAt = now;
        } else {
            this.bLastReadAt = now;
        }
    }

    public void touchLastMessage(Instant now) {
        this.lastMessageAt = now;
    }
}
