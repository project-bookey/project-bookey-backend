package app.bookey.api.club;

import app.bookey.api.club.ClubMeetingNoteService.MeetingNoteChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모임 노트 실시간 중계 — 같은 노트를 보고 있는 연결들에게 메시지를 퍼뜨린다.
 *
 * <p>서버가 여러 대 떠 있어도 같은 노트의 연결이 서로 다른 서버에 붙을 수 있다. 그래서 방송은 Redis pub/sub
 * ({@code meeting-note:{meetingId}}) 으로 모든 서버에 보내고, 각 서버는 받은 메시지를 자기에게 붙은 연결에만 전달한다.
 * Redis 가 안 되면 이 서버의 연결에만이라도 전달한다 — 다른 서버의 앱은 REST 폴링으로 따라잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingNoteRelay implements MessageListener {

    static final String CHANNEL_PREFIX = "meeting-note:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 이 서버에 붙은 연결 — 모임 id → 연결들. session 은 동시 전송에 안전한 데코레이터여야 한다. */
    private final Map<Long, Set<Peer>> rooms = new ConcurrentHashMap<>();

    /** 연결 하나. id 는 서버 사이에서도 유일한 UUID — 보낸 사람에게 되돌려 보내지 않는 데 쓴다. */
    public record Peer(String id, WebSocketSession session) {
    }

    public void join(Long meetingId, Peer peer) {
        rooms.computeIfAbsent(meetingId, k -> ConcurrentHashMap.newKeySet()).add(peer);
    }

    public void leave(Long meetingId, Peer peer) {
        rooms.computeIfPresent(meetingId, (k, peers) -> {
            peers.remove(peer);
            return peers.isEmpty() ? null : peers;
        });
    }

    /** 노트를 보고 있는 모든 연결에 보낸다. exceptPeerId 가 있으면 그 연결은 뺀다(자기 커서를 자기에게 돌려주지 않도록). */
    public void broadcast(Long meetingId, String exceptPeerId, Map<String, Object> message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("meetingId", meetingId);
        envelope.put("except", exceptPeerId);
        envelope.put("message", message);
        try {
            redis.convertAndSend(CHANNEL_PREFIX + meetingId, objectMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            log.warn("모임 노트 방송을 Redis 로 보내지 못해 이 서버의 연결에만 전달합니다: meetingId={}", meetingId, e);
            deliverLocal(meetingId, exceptPeerId, objectMapper.writeValueAsString(message));
        }
    }

    /** 연산이 커밋된 뒤에만 방송한다 — 롤백된 연산이 다른 멤버 화면에 나타나지 않도록. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(MeetingNoteChanged event) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "ops");
        message.put("version", event.version());
        message.put("ops", event.ops());
        message.put("by", event.by());
        message.put("clientId", event.clientId());
        broadcast(event.meetingId(), null, message);
    }

    /** Redis 에서 받은 방송 — 이 서버에 붙은 연결에 전달한다. */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(Message message, byte[] pattern) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), Map.class);
            if (!(envelope.get("meetingId") instanceof Number meetingId)) {
                return;
            }
            String except = envelope.get("except") instanceof String s ? s : null;
            deliverLocal(meetingId.longValue(), except, objectMapper.writeValueAsString(envelope.get("message")));
        } catch (RuntimeException e) {
            log.warn("모임 노트 방송을 읽지 못했습니다", e);
        }
    }

    private void deliverLocal(Long meetingId, String exceptPeerId, String json) {
        Set<Peer> peers = rooms.get(meetingId);
        if (peers == null) {
            return;
        }
        TextMessage text = new TextMessage(json);
        for (Peer peer : peers) {
            if (peer.id().equals(exceptPeerId)) {
                continue;
            }
            send(peer, text);
        }
    }

    /** 한 연결로 보낸다. 실패하면 그 연결은 닫는다 — 앱이 다시 연결하면서 노트를 새로 받는다. */
    public void send(Peer peer, TextMessage text) {
        WebSocketSession session = peer.session();
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(text);
        } catch (IOException | RuntimeException e) {
            log.debug("모임 노트 연결로 보내지 못했습니다: peer={}", peer.id(), e);
            try {
                session.close();
            } catch (IOException ignored) {
                // 이미 끊긴 연결이다.
            }
        }
    }
}
