package com.cardbattle.server.game;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 플레이어 연결 상태 (PRD FR-UI-02 좌석의 연결 상태, 9.4 PLAYER_CONNECTION).
 *
 * <p>STOMP 연결이 열리고 닫힐 때마다 플레이어별 연결 수를 센다 (탭을 여러 개 열 수 있다).
 * 연결이 하나도 없는 채로 {@link #OFFLINE_GRACE_MS} 가 지나야 "연결 끊김"으로 알린다 —
 * 새로고침처럼 곧바로 다시 붙는 경우까지 알리면 좌석이 깜빡이기 때문이다. 다시 붙으면 "다시 연결됨"을 알린다.
 *
 * <p>연결 상태는 게임 규칙이 아니라서 엔진 상태(GameState)에 넣지 않는다. 게임 버전을 올리지 않는 서버 메시지
 * {@code {type: "PLAYER_CONNECTION", payload: {playerId, connected}}} (seq·version 없음)로 보내므로,
 * 다른 사람이 행동 중이어도 STALE_VERSION 이 나지 않는다. 서버 1대 기준이라 메모리에 둔다 (방 락과 같다).
 */
@Component
public class PlayerPresence {

    /** 연결이 모두 끊긴 뒤 이만큼 지나야 "연결 끊김"으로 알린다 */
    static final long OFFLINE_GRACE_MS = 3_000;
    static final String MESSAGE_TYPE = "PLAYER_CONNECTION";

    private record Notice(String gameId, String playerId, boolean connected) {
    }

    private final GamePublisher publisher;
    /** STOMP 세션 ID → playerId (같은 끊김 이벤트가 두 번 와도 한 번만 센다) */
    private final Map<String, String> sessions = new HashMap<>();
    /** 연결이 모두 끊긴 플레이어 → 끊긴 시각 (아직 알리지 않음) */
    private final Map<String, Long> droppedAt = new LinkedHashMap<>();
    /** "연결 끊김"을 알린 플레이어 */
    private final Set<String> offline = new HashSet<>();
    /** 플레이어가 지금 하고 있는 게임 (알릴 곳) */
    private final Map<String, String> gameOf = new HashMap<>();

    public PlayerPresence(GamePublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String playerId = playerOf(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (playerId != null && sessionId != null) {
            connected(sessionId, playerId);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        disconnected(event.getSessionId(), System.currentTimeMillis());
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        sweep(System.currentTimeMillis());
    }

    /** 게임을 시작하거나 다시 불러올 때: 이 플레이어의 연결 상태는 이 게임에 알린다 */
    public synchronized void watch(String gameId, Collection<String> playerIds) {
        playerIds.forEach(id -> gameOf.put(id, gameId));
    }

    /** 스냅샷과 함께 보낼 "지금 끊겨 있는" 플레이어 (주어진 순서대로) */
    public synchronized List<String> offlineAmong(Collection<String> playerIds) {
        return playerIds.stream().filter(offline::contains).toList();
    }

    void connected(String sessionId, String playerId) {
        Notice notice = null;
        synchronized (this) {
            sessions.put(sessionId, playerId);
            droppedAt.remove(playerId);
            if (offline.remove(playerId)) {
                notice = notice(playerId, true);
            }
        }
        send(notice);
    }

    void disconnected(String sessionId, long now) {
        synchronized (this) {
            String playerId = sessions.remove(sessionId);
            if (playerId == null || sessions.containsValue(playerId) || offline.contains(playerId)) {
                return; // 모르는 세션, 이미 센 끊김, 아직 다른 탭이 열려 있음
            }
            droppedAt.put(playerId, now);
        }
    }

    /** 끊긴 지 유예 시간이 지난 플레이어를 "연결 끊김"으로 알린다 */
    void sweep(long now) {
        List<Notice> notices = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Map.Entry<String, Long>> it = droppedAt.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() >= OFFLINE_GRACE_MS) {
                    it.remove();
                    offline.add(e.getKey());
                    Notice notice = notice(e.getKey(), false);
                    if (notice != null) {
                        notices.add(notice);
                    }
                }
            }
        }
        notices.forEach(this::send);
    }

    /** 알릴 게임이 없으면(대기실 등) null. 연결 상태는 그대로 기억해 두었다가 스냅샷 때 알려 준다 */
    private Notice notice(String playerId, boolean connected) {
        String gameId = gameOf.get(playerId);
        return gameId == null ? null : new Notice(gameId, playerId, connected);
    }

    private void send(Notice notice) {
        if (notice != null) {
            publisher.broadcast(notice.gameId(), MESSAGE_TYPE, payload(notice.playerId(), notice.connected()));
        }
    }

    static Map<String, Object> payload(String playerId, boolean connected) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", playerId);
        payload.put("connected", connected);
        return payload;
    }

    private static String playerOf(Principal user) {
        return user == null ? null : user.getName();
    }
}
