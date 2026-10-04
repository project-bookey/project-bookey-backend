package app.bookey.api.club;

import app.bookey.api.club.ClubMeetingNoteService.MeetingNoteAccess;
import app.bookey.api.club.MeetingNoteRelay.Peer;
import app.bookey.api.club.dto.ClubMeetingNoteDtos.MeetingNoteOpsResult;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.TokenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모임 노트 실시간 연결 — {@code /ws/clubs/{clubId}/meetings/{meetingId}/note}.
 *
 * <p>브라우저 웹소켓은 헤더를 못 붙이고, 토큰을 URL 에 넣으면 접근 로그에 남는다. 그래서 연결한 뒤 첫 메시지로
 * {@code {"type":"auth","token":"...","clientId":"..."}} 를 받아 인증한다. 10초 안에 인증하지 않으면 닫는다.
 *
 * <p>받는 메시지:
 * <ul>
 *   <li>{@code ops {seq, ops[]}} — 연산 적용. 보낸 쪽엔 {@code ack {seq, version}}, 모두에겐 {@code ops} 방송.</li>
 *   <li>{@code presence {x?, y?, tool?}} — 커서·도구. 저장하지 않고 다른 연결에만 전달한다. 앱은 몇 초마다 다시 보내 살아 있음을 알린다.</li>
 *   <li>{@code ping} — {@code pong} 으로 답한다(중간 프록시가 쉬는 연결을 끊지 않도록).</li>
 * </ul>
 * 보내는 메시지: {@code ready {version, readOnly, peer, me}}, {@code ops}, {@code ack}, {@code presence}, {@code leave},
 * {@code closed}(노트를 마무리해 읽기만 된다), {@code error {seq?, code, message}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingNoteSocketHandler extends TextWebSocketHandler {

    static final Pattern PATH = Pattern.compile("^/ws/clubs/(\\d+)/meetings/(\\d+)/note/?$");
    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    /** 커서 방송 최소 간격 — 앱은 이보다 느리게 보내지만, 도배하는 연결을 막는다. */
    static final long PRESENCE_MIN_INTERVAL_MS = 50;
    static final int SEND_TIME_LIMIT_MS = 10_000;
    static final int SEND_BUFFER_LIMIT = 1024 * 1024;

    private static final String ATTR = "meetingNote";

    private final ClubMeetingNoteService noteService;
    private final MeetingNoteRelay relay;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    /** 아직 인증하지 않은 연결과 연결 시각 — 정리 스케줄러가 시간이 지난 연결을 닫는다. */
    private final Map<WebSocketSession, Instant> pending = new ConcurrentHashMap<>();

    /** 연결 하나의 상태. 인증 전에는 peer 가 null 이다. */
    private static final class State {
        final long clubId;
        final long meetingId;
        Peer peer;
        Long userId;
        Object me;
        String clientId;
        long lastPresenceAt;

        State(long clubId, long meetingId) {
            this.clubId = clubId;
            this.meetingId = meetingId;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        URI uri = session.getUri();
        Matcher m = uri == null ? null : PATH.matcher(uri.getPath());
        if (m == null || !m.matches()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put(ATTR, new State(Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
        pending.put(session, Instant.now());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        State state = (State) session.getAttributes().get(ATTR);
        if (state == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Map<String, Object> msg;
        try {
            msg = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (RuntimeException e) {
            reply(session, state, error(null, ErrorCode.INVALID_REQUEST, "노트 변경 내용을 저장하지 못했어요. 다시 시도해 주세요."));
            return;
        }
        String type = msg.get("type") instanceof String t ? t : "";
        if (state.peer == null) {
            if (!"auth".equals(type)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("auth required"));
                return;
            }
            authenticate(session, state, msg);
            return;
        }
        switch (type) {
            case "ops" -> applyOps(session, state, msg);
            case "presence" -> presence(state, msg);
            case "ping" -> reply(session, state, Map.of("type", "pong"));
            default -> reply(session, state, error(msg.get("seq"), ErrorCode.INVALID_REQUEST, "노트 변경 내용을 저장하지 못했어요. 다시 시도해 주세요."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        pending.remove(session);
        State state = (State) session.getAttributes().get(ATTR);
        if (state == null || state.peer == null) {
            return;
        }
        relay.leave(state.meetingId, state.peer);
        relay.broadcast(state.meetingId, state.peer.id(), Map.of("type", "leave", "peer", state.peer.id()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("모임 노트 연결 오류: {}", exception.toString());
    }

    /** 인증하지 않은 채 오래 머문 연결을 닫는다. */
    @Scheduled(fixedDelay = 5_000)
    public void closeUnauthenticated() {
        Instant deadline = Instant.now().minus(AUTH_TIMEOUT);
        pending.forEach((session, connectedAt) -> {
            if (connectedAt.isBefore(deadline)) {
                pending.remove(session);
                try {
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("auth timeout"));
                } catch (IOException ignored) {
                    // 이미 끊긴 연결이다.
                }
            }
        });
    }

    // ────────────────────────────── 메시지 ──────────────────────────────

    private void authenticate(WebSocketSession session, State state, Map<String, Object> msg) throws IOException {
        pending.remove(session);
        MeetingNoteAccess access;
        Long userId;
        try {
            String token = msg.get("token") instanceof String t ? t : "";
            userId = tokenProvider.subjectId(tokenProvider.parse(token, TokenType.USER_ACCESS));
            access = noteService.access(userId, state.clubId, state.meetingId);
        } catch (ApiException e) {
            // 만료 토큰이면 앱이 갱신한 뒤 다시 연결한다 — 코드를 먼저 알려 주고 닫는다.
            sendRaw(session, error(null, e.getErrorCode(), e.getMessage()));
            session.close(CloseStatus.POLICY_VIOLATION.withReason(e.getErrorCode().name()));
            return;
        }
        state.userId = userId;
        state.me = access.me();
        state.clientId = msg.get("clientId") instanceof String c && c.length() <= 64 ? c : null;
        state.peer = new Peer(UUID.randomUUID().toString(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT));
        relay.join(state.meetingId, state.peer);

        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("type", "ready");
        ready.put("version", access.version());
        ready.put("readOnly", access.readOnly());
        ready.put("peer", state.peer.id());
        ready.put("me", access.me());
        reply(session, state, ready);
        // 들어왔다고 알린다 — 다른 연결은 다음 presence 를 기다리지 않고 아바타를 띄운다.
        presence(state, Map.of());
    }

    private void applyOps(WebSocketSession session, State state, Map<String, Object> msg) throws IOException {
        Object seq = msg.get("seq");
        if (!(msg.get("ops") instanceof List<?> ops)) {
            reply(session, state, error(seq, ErrorCode.INVALID_REQUEST, "노트 변경 내용을 저장하지 못했어요. 다시 시도해 주세요."));
            return;
        }
        try {
            MeetingNoteOpsResult result = noteService.applyOps(state.userId, state.clubId, state.meetingId, ops, state.clientId);
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "ack");
            ack.put("seq", seq);
            ack.put("version", result.version());
            reply(session, state, ack);
        } catch (ApiException e) {
            reply(session, state, error(seq, e.getErrorCode(), e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("모임 노트 연산 적용 실패: meetingId={} userId={}", state.meetingId, state.userId, e);
            reply(session, state, error(seq, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage()));
        }
    }

    private void presence(State state, Map<String, Object> msg) {
        long now = System.currentTimeMillis();
        if (now - state.lastPresenceAt < PRESENCE_MIN_INTERVAL_MS) {
            return;
        }
        state.lastPresenceAt = now;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "presence");
        out.put("peer", state.peer.id());
        out.put("user", state.me);
        for (String key : List.of("x", "y")) {
            if (msg.get(key) instanceof Number n) {
                out.put(key, n);
            }
        }
        if (msg.get("tool") instanceof String tool && tool.length() <= 16) {
            out.put("tool", tool);
        }
        relay.broadcast(state.meetingId, state.peer.id(), out);
    }

    private void reply(WebSocketSession session, State state, Map<String, Object> message) throws IOException {
        if (state.peer != null) {
            relay.send(state.peer, new TextMessage(objectMapper.writeValueAsString(message)));
        } else {
            sendRaw(session, message);
        }
    }

    private void sendRaw(WebSocketSession session, Map<String, Object> message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    private static Map<String, Object> error(Object seq, ErrorCode code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "error");
        out.put("seq", seq);
        out.put("code", code.name());
        out.put("message", message);
        return out;
    }
}
