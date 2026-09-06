package app.bookey.domain.social;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최신순 첫 페이지. */
    List<ChatMessage> findAllByChatIdOrderByIdDesc(Long chatId, Pageable pageable);

    /** 커서 페이지 — beforeId 이전(더 오래된) 메시지 최신순. */
    List<ChatMessage> findAllByChatIdAndIdLessThanOrderByIdDesc(Long chatId, Long beforeId, Pageable pageable);

    /** 채팅방별 마지막 메시지 id — 목록 미리보기 배치 로딩용. */
    @Query("""
            SELECT m.chatId AS chatId, MAX(m.id) AS lastMessageId
            FROM ChatMessage m
            WHERE m.chatId IN :chatIds
            GROUP BY m.chatId
            """)
    List<LastMessageId> findLastMessageIds(@Param("chatIds") Collection<Long> chatIds);

    interface LastMessageId {
        Long getChatId();
        Long getLastMessageId();
    }

    /** 채팅방별 안읽음 수 — 상대가 보낸 메시지 중 내 마지막 읽은 시각 이후 것. */
    @Query("""
            SELECT m.chatId AS chatId, COUNT(m) AS unreadCount
            FROM ChatMessage m, Chat c
            WHERE c.id = m.chatId AND m.chatId IN :chatIds AND m.senderId <> :userId
              AND ((c.aUserId = :userId AND (c.aLastReadAt IS NULL OR m.createdAt > c.aLastReadAt))
                OR (c.bUserId = :userId AND (c.bLastReadAt IS NULL OR m.createdAt > c.bLastReadAt)))
            GROUP BY m.chatId
            """)
    List<UnreadCount> countUnreadPerChat(@Param("userId") Long userId,
                                         @Param("chatIds") Collection<Long> chatIds);

    interface UnreadCount {
        Long getChatId();
        long getUnreadCount();
    }
}
