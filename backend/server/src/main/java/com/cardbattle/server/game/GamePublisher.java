package com.cardbattle.server.game;

import com.cardbattle.engine.event.GameEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게임 이벤트를 STOMP로 보낸다.
 * 공개 이벤트 → /topic/games/{gameId}, 개인 이벤트 → /user/queue/games/{gameId}
 * 모든 메시지는 {type, payload, seq?, version?} 모양이라 프론트가 한 가지 방식으로 처리한다.
 */
@Component
public class GamePublisher {

    private final SimpMessagingTemplate messaging;

    public GamePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publish(String gameId, List<GameEvent> events) {
        for (GameEvent event : events) {
            if (event.publicEvent()) {
                messaging.convertAndSend("/topic/games/" + gameId, event);
            } else {
                messaging.convertAndSendToUser(event.recipientId(), queue(gameId), event);
            }
        }
    }

    public void sendToPlayer(String gameId, String playerId, String type, Object payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("payload", payload);
        messaging.convertAndSendToUser(playerId, queue(gameId), message);
    }

    public void reject(String gameId, String playerId, String code, String reason, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("reason", reason);
        payload.put("message", message);
        sendToPlayer(gameId, playerId, "ACTION_REJECTED", payload);
    }

    private static String queue(String gameId) {
        return "/queue/games/" + gameId;
    }
}
