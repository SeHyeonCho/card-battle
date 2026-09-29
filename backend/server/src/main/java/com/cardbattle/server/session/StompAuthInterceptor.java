package com.cardbattle.server.session;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 프레임의 X-Session-Token 헤더로 사용자를 인증한다.
 * 토큰이 없거나 틀리면 연결을 거부한다.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String TOKEN_HEADER = "X-Session-Token";

    private final SessionService sessions;

    public StompAuthInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader(TOKEN_HEADER);
            Session session = sessions.find(token)
                    .orElseThrow(() -> new MessagingException("세션이 없거나 만료됐습니다"));
            accessor.setUser(new StompPrincipal(session.playerId()));
        }
        return message;
    }
}
