package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅 메시지 — 텍스트 또는 BOOKEY 이모티콘. */
@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private ChatMessageType type = ChatMessageType.TEXT;

    @Column(name = "sticker_code", length = 100)
    private String stickerCode;

    @Builder
    private ChatMessage(Long chatId, Long senderId, String body, ChatMessageType type, String stickerCode) {
        this.chatId = chatId;
        this.senderId = senderId;
        this.body = body;
        this.type = type == null ? ChatMessageType.TEXT : type;
        this.stickerCode = stickerCode;
    }
}
