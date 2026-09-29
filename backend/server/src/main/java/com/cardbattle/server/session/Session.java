package com.cardbattle.server.session;

/**
 * 게스트 세션 (FR-AUTH-01). token은 비밀값이라 본인에게만 주고,
 * 다른 플레이어에게는 playerId만 보인다.
 */
public record Session(String token, String playerId, String nickname) {
}
