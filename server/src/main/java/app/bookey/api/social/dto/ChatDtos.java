package app.bookey.api.social.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import app.bookey.domain.social.ChatMessageType;

import java.time.Instant;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {}

    /** 채팅방 열기 — 맞팔로우인 상대만. 이미 있으면 그 방을 돌려준다. */
    public record OpenChatRequest(@NotNull Long userId) {}

    public record ChatSummaryView(
            @NotNull Long id,
            @NotNull Long otherUserId,
            @NotNull String otherNickname,
            String otherAvatarUrl,
            String lastMessageBody,
            ChatMessageType lastMessageType,
            String lastStickerCode,
            Instant lastMessageAt,
            long unreadCount,
            @NotNull Instant createdAt
    ) {}

    public record SendMessageRequest(
            @Size(max = 1000) String body,
            ChatMessageType type,
            @Size(max = 100) String stickerCode
    ) {
        public SendMessageRequest(String body) {
            this(body, null, null);
        }
    }

    public record ChatMessageView(
            @NotNull Long id,
            @NotNull Long chatId,
            @NotNull Long senderId,
            @NotNull String body,
            @NotNull ChatMessageType type,
            String stickerCode,
            boolean mine,
            @NotNull Instant createdAt
    ) {}

    /** 커서 페이지 — messages 는 최신순. nextBeforeId 가 null 이면 더 오래된 메시지가 없다. */
    public record ChatMessagesView(
            @NotNull List<ChatMessageView> messages,
            Long nextBeforeId
    ) {}
}
