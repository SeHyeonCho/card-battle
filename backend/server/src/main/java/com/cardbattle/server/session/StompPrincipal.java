package com.cardbattle.server.session;

import java.security.Principal;

/** STOMP 연결의 사용자. getName()이 playerId라서 /user/{playerId}/queue/... 로 개인 메시지가 간다. */
public record StompPrincipal(String playerId) implements Principal {

    @Override
    public String getName() {
        return playerId;
    }
}
