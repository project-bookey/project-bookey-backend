package app.bookey.api.social;

import app.bookey.api.social.dto.ChatDtos.ChatMessageView;
import app.bookey.api.social.dto.ChatDtos.ChatMessagesView;
import app.bookey.api.social.dto.ChatDtos.ChatSummaryView;
import app.bookey.api.social.dto.ChatDtos.SendMessageRequest;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.social.Chat;
import app.bookey.domain.social.ChatMessage;
import app.bookey.domain.social.ChatMessageRepository;
import app.bookey.domain.social.ChatMessageRepository.LastMessageId;
import app.bookey.domain.social.ChatMessageRepository.UnreadCount;
import app.bookey.domain.social.ChatMessageType;
import app.bookey.domain.social.ChatRepository;
import app.bookey.domain.social.PostcardRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 1:1 채팅 (§14.3) — 엽서 답장이 오간 사이만 연다. 팔로우와는 무관하다.
 * 실시간 인프라 없이 폴링으로 시작한다 (기획서 §13-11 결정).
 * 조건은 새 방을 만들 때만 본다 — 한 번 열린 방은 참가자끼리 계속 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 도배 방지 — 1분에 30건. */
    private static final int MESSAGE_RATE_LIMIT = 30;
    /** 메시지 커서 페이지 크기. */
    private static final int MESSAGE_PAGE_SIZE = 30;

    private final ChatRepository chatRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PostcardRepository postcardRepository;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    /** 채팅방 열기 — 이미 있으면 그 방. 없으면 엽서 답장이 오간 사이여야 한다(아니면 CHAT_NOT_ALLOWED). */
    @Transactional
    public ChatSummaryView open(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "나 자신과는 채팅할 수 없어요.");
        }
        User other = userRepository.findById(otherUserId)
                .filter(found -> found.getStatus() != UserStatus.TERMINATED)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Chat chat = findPair(userId, otherUserId).orElseGet(() -> {
            if (!postcardRepository.existsRepliedBetween(userId, otherUserId)) {
                throw ApiException.of(ErrorCode.CHAT_NOT_ALLOWED);
            }
            return chatRepository.save(Chat.of(userId, otherUserId));
        });
        return toSummary(chat, userId, other, null, 0L);
    }

    /** 내 채팅 목록 — 상대 정보 · 마지막 메시지 · 안읽음 수. */
    @Transactional(readOnly = true)
    public PageResponse<ChatSummaryView> list(Long userId, Pageable pageable) {
        Page<Chat> page = chatRepository.findAllMine(userId, pageable);
        List<Long> chatIds = page.getContent().stream().map(Chat::getId).toList();

        Map<Long, User> others = loadOthers(page.getContent(), userId);
        Map<Long, ChatMessage> lastMessages = loadLastMessages(chatIds);
        Map<Long, Long> unread = chatIds.isEmpty() ? Map.of()
                : messageRepository.countUnreadPerChat(userId, chatIds).stream()
                        .collect(Collectors.toMap(UnreadCount::getChatId, UnreadCount::getUnreadCount));

        return PageResponse.of(page, chat -> toSummary(chat, userId,
                others.get(chat.counterpartOf(userId)),
                lastMessages.get(chat.getId()),
                unread.getOrDefault(chat.getId(), 0L)));
    }

    /**
     * 메시지 커서 페이지 (최신순) — 읽는 순간 내 읽음 시각을 갱신한다.
     * beforeId 가 없으면 첫 페이지.
     */
    @Transactional
    public ChatMessagesView messages(Long userId, Long chatId, Long beforeId) {
        Chat chat = participantChat(userId, chatId);
        Pageable pageable = PageRequest.of(0, MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = beforeId == null
                ? messageRepository.findAllByChatIdOrderByIdDesc(chatId, pageable)
                : messageRepository.findAllByChatIdAndIdLessThanOrderByIdDesc(chatId, beforeId, pageable);
        // 첫 페이지를 연 것만 읽음으로 본다 — 과거로 스크롤하는 중에는 갱신할 필요가 없다.
        if (beforeId == null) {
            chat.markRead(userId, Instant.now(clock));
        }
        Long nextBeforeId = messages.size() < MESSAGE_PAGE_SIZE
                ? null
                : messages.get(messages.size() - 1).getId();
        return new ChatMessagesView(
                messages.stream().map(m -> toMessageView(m, userId)).toList(),
                nextBeforeId);
    }

    @Transactional
    public ChatMessageView send(Long userId, Long chatId, SendMessageRequest request) {
        Chat chat = participantChat(userId, chatId);
        rateLimiter.require("chat:send:" + userId, MESSAGE_RATE_LIMIT, Duration.ofMinutes(1));
        MessagePayload payload = messagePayload(request);

        Instant now = Instant.now(clock);
        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .chatId(chatId)
                .senderId(userId)
                .body(payload.body())
                .type(payload.type())
                .stickerCode(payload.stickerCode())
                .build());
        chat.touchLastMessage(now);
        chat.markRead(userId, now);   // 내가 보낸 직후의 내 안읽음은 0 이어야 한다
        notifyMessageReceived(userId, chat.counterpartOf(userId), chatId, message);
        return toMessageView(message, userId);
    }

    /** 채팅방 삭제 — 참가자만. DB FK 가 메시지를 함께 지운다. */
    @Transactional
    public void delete(Long userId, Long chatId) {
        Chat chat = participantChat(userId, chatId);
        chatRepository.delete(chat);
    }

    /**
     * 없는 방과 남의 방은 똑같이 CHAT_NOT_FOUND — 방의 존재를 드러내지 않는다.
     * 상대가 탈퇴한 방도 없는 것으로 본다 — 그 사람의 메시지가 남아 있다(30일 뒤 계정과 함께 지워진다).
     */
    private Chat participantChat(Long userId, Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CHAT_NOT_FOUND));
        if (!chat.isParticipant(userId) || userRepository.isTerminated(chat.counterpartOf(userId))) {
            throw ApiException.of(ErrorCode.CHAT_NOT_FOUND);
        }
        return chat;
    }

    /** 채팅을 열 수 있는가 — 이미 방이 있거나 엽서 답장이 오간 사이. 프로필의 채팅 버튼이 쓴다. */
    @Transactional(readOnly = true)
    public boolean canChat(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId) || userRepository.isTerminated(otherUserId)) {
            return false;
        }
        return findPair(userId, otherUserId).isPresent()
                || postcardRepository.existsRepliedBetween(userId, otherUserId);
    }

    private Optional<Chat> findPair(Long a, Long b) {
        return chatRepository.findPair(Math.min(a, b), Math.max(a, b));
    }

    private Map<Long, User> loadOthers(List<Chat> chats, Long userId) {
        List<Long> ids = chats.stream().map(chat -> chat.counterpartOf(userId)).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, ChatMessage> loadLastMessages(List<Long> chatIds) {
        if (chatIds.isEmpty()) {
            return Map.of();
        }
        List<Long> lastIds = messageRepository.findLastMessageIds(chatIds).stream()
                .map(LastMessageId::getLastMessageId)
                .toList();
        if (lastIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findAllById(lastIds).stream()
                .collect(Collectors.toMap(ChatMessage::getChatId, Function.identity()));
    }

    private void notifyMessageReceived(Long senderId, Long recipientId, Long chatId, ChatMessage message) {
        User sender = userRepository.findById(senderId).orElse(null);
        String nickname = sender == null ? "상대" : sender.getNickname();
        notificationService.inApp(new NotificationService.NotificationRequest(
                recipientId, NotificationType.CHAT_MESSAGE, null, null, null,
                "새 메시지가 왔어요",
                nickname + (message.getType() == ChatMessageType.STICKER
                        ? "님이 이모티콘을 보냈어요."
                        : "님이 메시지를 보냈어요."),
                Map.of("chatId", chatId, "messageId", message.getId(), "fromUserId", senderId), null));
    }

    private ChatSummaryView toSummary(Chat chat, Long userId, User other,
                                      ChatMessage lastMessage, long unreadCount) {
        return new ChatSummaryView(
                chat.getId(),
                chat.counterpartOf(userId),
                other == null ? "알 수 없음" : other.getNickname(),
                other == null ? null : other.getAvatarUrl(),
                lastMessage == null ? null : lastMessage.getBody(),
                lastMessage == null ? null : lastMessage.getType(),
                lastMessage == null ? null : lastMessage.getStickerCode(),
                lastMessage == null ? chat.getLastMessageAt() : lastMessage.getCreatedAt(),
                unreadCount,
                chat.getCreatedAt() == null ? Instant.now(clock) : chat.getCreatedAt());
    }

    private ChatMessageView toMessageView(ChatMessage message, Long viewerId) {
        return new ChatMessageView(
                message.getId(), message.getChatId(), message.getSenderId(), message.getBody(),
                message.getType(), message.getStickerCode(),
                message.getSenderId().equals(viewerId),
                message.getCreatedAt() == null ? Instant.now(clock) : message.getCreatedAt());
    }

    private MessagePayload messagePayload(SendMessageRequest request) {
        String body = trimToNull(request.body());
        String explicitSticker = trimToNull(request.stickerCode());
        if (request.type() == ChatMessageType.TEXT && explicitSticker != null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "보낼 수 없는 이모티콘이에요.");
        }
        boolean stickerRequest = request.type() == ChatMessageType.STICKER || explicitSticker != null;

        if (stickerRequest) {
            String code = explicitSticker == null ? body : explicitSticker;
            if (!BookeyStickerRegistry.isStickerCode(code)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "보낼 수 없는 이모티콘이에요.");
            }
            return new MessagePayload(code, ChatMessageType.STICKER, code);
        }

        if (BookeyStickerRegistry.looksLikeStickerCode(body)) {
            if (!BookeyStickerRegistry.isStickerCode(body)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "보낼 수 없는 이모티콘이에요.");
            }
            return new MessagePayload(body, ChatMessageType.STICKER, body);
        }
        if (body == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "메시지를 적어 주세요.");
        }
        return new MessagePayload(body, ChatMessageType.TEXT, null);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record MessagePayload(String body, ChatMessageType type, String stickerCode) {}
}
