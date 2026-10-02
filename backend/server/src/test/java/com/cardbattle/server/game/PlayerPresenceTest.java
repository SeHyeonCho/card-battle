package com.cardbattle.server.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cardbattle.server.game.PlayerPresence.OFFLINE_GRACE_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 좌석 연결 상태 (FR-UI-02, PLAYER_CONNECTION): 끊김은 유예 시간 뒤에, 다시 연결되면 바로 알린다 */
class PlayerPresenceTest {

    /** 보낸 PLAYER_CONNECTION 을 "게임:플레이어:연결여부" 로 모은다 */
    private static final class Sent extends GamePublisher {
        final List<String> notices = new ArrayList<>();

        Sent() {
            super(null);
        }

        @Override
        public void broadcast(String gameId, String type, Object payload) {
            Map<?, ?> p = (Map<?, ?>) payload;
            notices.add(gameId + ":" + p.get("playerId") + ":" + p.get("connected") + ":" + type);
        }
    }

    private final Sent sent = new Sent();
    private final PlayerPresence presence = new PlayerPresence(sent);

    private void inGame(String... playerIds) {
        presence.watch("g1", List.of(playerIds));
    }

    @Test
    @DisplayName("연결이 모두 끊기고 유예 시간이 지나면 그 게임에 '연결 끊김'을 알린다")
    void announcesOfflineAfterGrace() {
        inGame("a", "b");
        presence.connected("s1", "a");
        presence.disconnected("s1", 1_000);

        presence.sweep(1_000 + OFFLINE_GRACE_MS - 1);
        assertTrue(sent.notices.isEmpty(), "유예 시간 전에는 알리지 않는다");

        presence.sweep(1_000 + OFFLINE_GRACE_MS);
        assertEquals(List.of("g1:a:false:PLAYER_CONNECTION"), sent.notices);
        assertEquals(List.of("a"), presence.offlineAmong(List.of("a", "b")));

        presence.sweep(1_000 + OFFLINE_GRACE_MS * 3);
        assertEquals(1, sent.notices.size(), "한 번만 알린다");
    }

    @Test
    @DisplayName("다시 연결되면 바로 '다시 연결됨'을 알리고, 끊긴 목록에서 빠진다")
    void announcesReconnect() {
        inGame("a");
        presence.connected("s1", "a");
        presence.disconnected("s1", 0);
        presence.sweep(OFFLINE_GRACE_MS);

        presence.connected("s2", "a");

        assertEquals(List.of("g1:a:false:PLAYER_CONNECTION", "g1:a:true:PLAYER_CONNECTION"), sent.notices);
        assertTrue(presence.offlineAmong(List.of("a")).isEmpty());
    }

    @Test
    @DisplayName("새로고침처럼 유예 시간 안에 다시 붙으면 아무것도 알리지 않는다")
    void quickReconnectIsSilent() {
        inGame("a");
        presence.connected("s1", "a");
        presence.disconnected("s1", 0);
        presence.connected("s2", "a");
        presence.sweep(OFFLINE_GRACE_MS * 2);

        assertTrue(sent.notices.isEmpty());
    }

    @Test
    @DisplayName("탭이 여러 개면 모두 닫혀야 끊긴 것이고, 같은 끊김 이벤트가 두 번 와도 한 번만 센다")
    void countsSessions() {
        inGame("a");
        presence.connected("s1", "a");
        presence.connected("s2", "a");

        presence.disconnected("s1", 0);
        presence.disconnected("s1", 0);
        presence.sweep(OFFLINE_GRACE_MS);
        assertTrue(sent.notices.isEmpty(), "s2 가 아직 열려 있다");

        presence.disconnected("s2", OFFLINE_GRACE_MS);
        presence.sweep(OFFLINE_GRACE_MS * 2);
        assertEquals(List.of("g1:a:false:PLAYER_CONNECTION"), sent.notices);
    }

    @Test
    @DisplayName("게임이 없을 때(대기실) 끊긴 사람은 알리지 않지만 기억해 두었다가 스냅샷 때 알려 준다")
    void remembersOfflineWithoutGame() {
        presence.connected("s1", "a");
        presence.disconnected("s1", 0);
        presence.sweep(OFFLINE_GRACE_MS);
        assertTrue(sent.notices.isEmpty());

        inGame("a", "b");
        assertEquals(List.of("a"), presence.offlineAmong(List.of("b", "a")));

        presence.connected("s2", "a");
        assertEquals(List.of("g1:a:true:PLAYER_CONNECTION"), sent.notices, "게임이 생긴 뒤 다시 붙으면 알린다");
    }

    @Test
    @DisplayName("모르는 세션(인증 실패 등)의 끊김은 무시한다")
    void ignoresUnknownSession() {
        inGame("a");
        presence.disconnected("nope", 0);
        presence.sweep(OFFLINE_GRACE_MS * 2);
        assertTrue(sent.notices.isEmpty());
        assertTrue(presence.offlineAmong(List.of("a")).isEmpty());
    }
}
