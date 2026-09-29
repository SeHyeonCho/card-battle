package com.cardbattle.server.game;

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
 * 게임 STOMP 메시지 (PRD 9.2).
 * /app/games/{gameId}/play | discard | sync
 */
@Controller
public class GameMessageController {

    private final GameService games;

    public GameMessageController(GameService games) {
        this.games = games;
    }

    @MessageMapping("/games/{gameId}/play")
    public void play(@DestinationVariable String gameId, @Payload GameService.PlayRequest request,
                     Principal principal) {
        games.play(gameId, principal.getName(), request);
    }

    @MessageMapping("/games/{gameId}/discard")
    public void discard(@DestinationVariable String gameId, @Payload GameService.DiscardRequest request,
                        Principal principal) {
        games.discard(gameId, principal.getName(), request);
    }

    @MessageMapping("/games/{gameId}/sync")
    public void sync(@DestinationVariable String gameId, Principal principal) {
        games.sync(gameId, principal.getName());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, Object> onError(ApiException e) {
        return Map.of("code", e.code(), "message", e.getMessage());
    }
}
