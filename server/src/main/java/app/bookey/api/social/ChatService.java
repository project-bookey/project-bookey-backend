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
import app.bookey.domain.social.ChatRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 1:1 채팅 (§14.3) — 맞팔로우끼리만. 실시간 인프라 없이 폴링으로 시작한다 (기획서 §13-11 결정).
 * 열기·보내기 모두 맞팔 상태를 검사한다 — 언팔로우하면 기존 방이 있어도 더 보낼 수 없다.
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
    private final FollowService followService;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    /** 채팅방 열기 — 이미 있으면 그 방. 맞팔로우가 아니면 CHAT_NOT_ALLOWED. */
    @Transactional
    public ChatSummaryView open(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "자신과는 채팅할 수 없습니다.");
        }
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireMutual(userId, otherUserId);
        Chat chat = chatRepository
                .findPair(Math.min(userId, otherUserId), Math.max(userId, otherUserId))
                .orElseGet(() -> chatRepository.save(Chat.of(userId, otherUserId)));
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
        // 언팔로우된 관계에는 더 보낼 수 없다 — 방은 남지만 쓰기가 막힌다.
        requireMutual(userId, chat.counterpartOf(userId));
        rateLimiter.require("chat:send:" + userId, MESSAGE_RATE_LIMIT, Duration.ofMinutes(1));

        Instant now = Instant.now(clock);
        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .chatId(chatId).senderId(userId).body(request.body().trim())
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

    /** 없는 방과 남의 방은 똑같이 CHAT_NOT_FOUND — 방의 존재를 드러내지 않는다. */
    private Chat participantChat(Long userId, Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CHAT_NOT_FOUND));
        if (!chat.isParticipant(userId)) {
            throw ApiException.of(ErrorCode.CHAT_NOT_FOUND);
        }
        return chat;
    }

    private void requireMutual(Long userId, Long otherUserId) {
        if (!followService.isMutual(userId, otherUserId)) {
            throw ApiException.of(ErrorCode.CHAT_NOT_ALLOWED);
        }
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
                "새 채팅이 도착했어요",
                nickname + "님이 메시지를 보냈습니다.",
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
                lastMessage == null ? chat.getLastMessageAt() : lastMessage.getCreatedAt(),
                unreadCount,
                chat.getCreatedAt() == null ? Instant.now(clock) : chat.getCreatedAt());
    }

    private ChatMessageView toMessageView(ChatMessage message, Long viewerId) {
        return new ChatMessageView(
                message.getId(), message.getChatId(), message.getSenderId(), message.getBody(),
                message.getSenderId().equals(viewerId),
                message.getCreatedAt() == null ? Instant.now(clock) : message.getCreatedAt());
    }
}
