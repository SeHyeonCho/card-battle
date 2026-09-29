package com.cardbattle.server.session;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/sessions {nickname} → {token, playerId, nickname} (PRD 9.1) */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public record CreateSessionRequest(String nickname) {
    }

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public Session create(@RequestBody CreateSessionRequest request) {
        return sessions.create(request.nickname());
    }
}
