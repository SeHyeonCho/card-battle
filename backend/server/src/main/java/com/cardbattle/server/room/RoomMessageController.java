package com.cardbattle.server.room;

import com.cardbattle.server.common.ApiException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * 대기실 STOMP 메시지 (PRD 9.2).
 * 오류는 보낸 사람에게만 /user/queue/errors 로 돌려준다.
 */
@Controller
public class RoomMessageController {

    public record ReadyRequest(Boolean ready) {
    }

    public record KickRequest(String playerId) {
    }

    /** PRD 9.2: {settings} */
    public record SettingsRequest(RoomSettings.Request settings) {
    }

    private final RoomService rooms;

    public RoomMessageController(RoomService rooms) {
        this.rooms = rooms;
    }

    @MessageMapping("/rooms/{roomId}/ready")
    public void ready(@DestinationVariable String roomId, @Payload ReadyRequest request, Principal principal) {
        rooms.setReady(roomId, principal.getName(), Boolean.TRUE.equals(request.ready()));
    }

    @MessageMapping("/rooms/{roomId}/start")
    public void start(@DestinationVariable String roomId, Principal principal) {
        rooms.start(roomId, principal.getName());
    }

    @MessageMapping("/rooms/{roomId}/leave")
    public void leave(@DestinationVariable String roomId, Principal principal) {
        rooms.leave(roomId, principal.getName());
    }

    @MessageMapping("/rooms/{roomId}/settings")
    public void settings(@DestinationVariable String roomId, @Payload SettingsRequest request, Principal principal) {
        rooms.updateSettings(roomId, principal.getName(), request.settings());
    }

    @MessageMapping("/rooms/{roomId}/kick")
    public void kick(@DestinationVariable String roomId, @Payload KickRequest request, Principal principal) {
        rooms.kick(roomId, principal.getName(), request.playerId());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, Object> onError(ApiException e) {
        return Map.of("code", e.code(), "message", e.getMessage());
    }
}
