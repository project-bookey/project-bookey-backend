package app.bookey.api.social;

import app.bookey.api.social.dto.ChatDtos.ChatMessageView;
import app.bookey.api.social.dto.ChatDtos.ChatMessagesView;
import app.bookey.api.social.dto.ChatDtos.ChatSummaryView;
import app.bookey.api.social.dto.ChatDtos.SendMessageRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.social.Chat;
import app.bookey.domain.social.ChatMessage;
import app.bookey.domain.social.ChatMessageRepository;
import app.bookey.domain.social.ChatRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 1:1 채팅 계약 단위 테스트 (§14.3) — 맞팔로우 게이트, 쌍 정규화, 참가자 검사, 읽음 처리. */
class ChatServiceTest {

    private final ChatRepository chatRepository = mock(ChatRepository.class);
    private final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FollowService followService = mock(FollowService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final Clock clock = mock(Clock.class);
    private final ChatService service = new ChatService(
            chatRepository, messageRepository, userRepository, followService, rateLimiter, clock);

    private static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("필드를 찾을 수 없습니다: " + field);
    }

    private static void assertApiError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private User other(long id) {
        User user = User.builder().handle("other" + id).email("o@dev.local").nickname("상대").build();
        set(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("열기 — 맞팔로우면 쌍을 정규화(a<b)해 방을 만든다. 순서가 뒤집혀도 같은 방")
    void openNormalizesPair() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        other(2L);
        when(followService.isMutual(5L, 2L)).thenReturn(true);
        when(chatRepository.findPair(2L, 5L)).thenReturn(Optional.empty());
        ArgumentCaptor<Chat> saved = ArgumentCaptor.forClass(Chat.class);
        when(chatRepository.save(saved.capture())).thenAnswer(inv -> {
            Chat chat = inv.getArgument(0);
            set(chat, "id", 10L);
            return chat;
        });

        ChatSummaryView view = service.open(5L, 2L);   // 큰 id 가 먼저 열어도

        assertThat(saved.getValue().getAUserId()).isEqualTo(2L);
        assertThat(saved.getValue().getBUserId()).isEqualTo(5L);
        assertThat(view.otherUserId()).isEqualTo(2L);
        assertThat(view.otherNickname()).isEqualTo("상대");
        assertThat(view.unreadCount()).isZero();
    }

    @Test
    @DisplayName("열기 — 이미 방이 있으면 새로 만들지 않고 그 방을 돌려준다")
    void openReturnsExisting() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        other(2L);
        when(followService.isMutual(1L, 2L)).thenReturn(true);
        Chat existing = Chat.of(1L, 2L);
        set(existing, "id", 10L);
        when(chatRepository.findPair(1L, 2L)).thenReturn(Optional.of(existing));

        ChatSummaryView view = service.open(1L, 2L);

        assertThat(view.id()).isEqualTo(10L);
        verify(chatRepository, never()).save(any());
    }

    @Test
    @DisplayName("열기 — 맞팔로우가 아니면 CHAT_NOT_ALLOWED, 자신과는 INVALID_REQUEST")
    void openGuards() {
        other(2L);
        when(followService.isMutual(1L, 2L)).thenReturn(false);
        assertApiError(() -> service.open(1L, 2L), ErrorCode.CHAT_NOT_ALLOWED);

        assertApiError(() -> service.open(1L, 1L), ErrorCode.INVALID_REQUEST);
        verify(chatRepository, never()).save(any());
    }

    @Test
    @DisplayName("보내기 — 참가자가 아니면 CHAT_NOT_FOUND (방의 존재를 드러내지 않음)")
    void sendByNonParticipant() {
        Chat chat = Chat.of(1L, 2L);
        set(chat, "id", 10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));

        assertApiError(() -> service.send(9L, 10L, new SendMessageRequest("몰래")),
                ErrorCode.CHAT_NOT_FOUND);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("보내기 — 언팔로우된 상대에게는 CHAT_NOT_ALLOWED (방은 남지만 쓰기가 막힌다)")
    void sendAfterUnfollow() {
        Chat chat = Chat.of(1L, 2L);
        set(chat, "id", 10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(followService.isMutual(1L, 2L)).thenReturn(false);

        assertApiError(() -> service.send(1L, 10L, new SendMessageRequest("아직 있니")),
                ErrorCode.CHAT_NOT_ALLOWED);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("보내기 — 저장하고 마지막 메시지 시각과 내 읽음 시각을 함께 갱신한다")
    void sendUpdatesChat() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Chat chat = Chat.of(1L, 2L);
        set(chat, "id", 10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(followService.isMutual(1L, 2L)).thenReturn(true);
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage message = inv.getArgument(0);
            set(message, "id", 100L);
            return message;
        });

        ChatMessageView view = service.send(1L, 10L, new SendMessageRequest("  잘 지내요?  "));

        assertThat(view.body()).isEqualTo("잘 지내요?");
        assertThat(view.mine()).isTrue();
        assertThat(chat.getLastMessageAt()).isEqualTo(now);
        assertThat(chat.lastReadOf(1L)).isEqualTo(now);   // 내가 보낸 직후 내 안읽음은 0
        assertThat(chat.lastReadOf(2L)).isNull();
        verify(rateLimiter).require(eq("chat:send:1"), eq(30), any());
    }

    @Test
    @DisplayName("메시지 — 첫 페이지를 열면 읽음 처리되고, 과거 페이지(beforeId)는 읽음을 건드리지 않는다")
    void messagesMarkReadOnlyOnFirstPage() {
        Instant now = Instant.parse("2026-09-06T03:00:00Z");
        when(clock.instant()).thenReturn(now);
        Chat chat = Chat.of(1L, 2L);
        set(chat, "id", 10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findAllByChatIdOrderByIdDesc(eq(10L), any())).thenReturn(List.of());
        when(messageRepository.findAllByChatIdAndIdLessThanOrderByIdDesc(eq(10L), eq(50L), any()))
                .thenReturn(List.of());

        ChatMessagesView first = service.messages(1L, 10L, null);
        assertThat(first.messages()).isEmpty();
        assertThat(first.nextBeforeId()).isNull();
        assertThat(chat.lastReadOf(1L)).isEqualTo(now);

        set(chat, "aLastReadAt", null);
        service.messages(1L, 10L, 50L);
        assertThat(chat.lastReadOf(1L)).isNull();
    }

    @Test
    @DisplayName("삭제 — 참가자만 방을 삭제할 수 있고, 남의 방은 없는 것처럼 보인다")
    void deleteOnlyParticipant() {
        Chat chat = Chat.of(1L, 2L);
        set(chat, "id", 10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));

        service.delete(1L, 10L);
        verify(chatRepository).delete(chat);

        assertApiError(() -> service.delete(9L, 10L), ErrorCode.CHAT_NOT_FOUND);
    }
}
