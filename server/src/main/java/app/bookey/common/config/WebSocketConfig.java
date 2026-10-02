package app.bookey.common.config;

import app.bookey.api.club.MeetingNoteRelay;
import app.bookey.api.club.MeetingNoteSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 실시간 연결(웹소켓). 지금은 모임 공유 노트 하나다.
 *
 * <p>인증은 쿠키가 아니라 첫 메시지의 토큰으로 하므로 Origin 을 막을 이유가 없다(네이티브 앱은 Origin 을 안 보내기도 한다).
 * 서버 사이 방송은 Redis pub/sub 으로 한다 — {@link MeetingNoteRelay}.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** 획 하나(점 800개)나 사진 여러 장을 한 번에 붙여도 들어가게 넉넉히 — 문서 상한(1MB)과 같다. */
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;

    private final MeetingNoteSocketHandler meetingNoteSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(meetingNoteSocketHandler, "/ws/clubs/*/meetings/*/note")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }

    @Bean
    public RedisMessageListenerContainer meetingNoteListenerContainer(RedisConnectionFactory connectionFactory,
                                                                      MeetingNoteRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new PatternTopic("meeting-note:*"));
        return container;
    }
}
