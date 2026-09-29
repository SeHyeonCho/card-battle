package com.cardbattle.server.config;

import com.cardbattle.server.session.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정 (PRD 9.2).
 *
 * <ul>
 *   <li>/app/...        클라이언트 → 서버 (@MessageMapping)</li>
 *   <li>/topic/...      서버 → 방/게임 전체 (공개 이벤트)</li>
 *   <li>/user/queue/... 서버 → 특정 플레이어 (손패, 거절 사유, 스냅샷)</li>
 * </ul>
 *
 * Phase 1은 서버 1대 기준이라 내장 SimpleBroker를 쓴다.
 * 서버를 여러 대로 늘릴 때 Redis Pub/Sub 재전송을 붙인다 (PRD 12장).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor authInterceptor;
    private final AppProperties props;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, AppProperties props) {
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(props.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        // 같은 세션으로 가는 메시지의 순서를 보장한다 (공개/개인 이벤트가 섞여도 순서 유지)
        config.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
