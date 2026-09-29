package com.cardbattle.server.game;

import com.cardbattle.engine.GameEngine;
import com.cardbattle.engine.PlayerSeed;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.Rejection;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.pack.PackCatalog;
import com.cardbattle.server.room.Room;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 게임 진행. 흐름은 항상 같다:
 * <ol>
 *   <li>Redis에서 상태를 읽는다</li>
 *   <li>엔진에 명령을 넘긴다 (규칙 판정은 전부 엔진이 한다)</li>
 *   <li>버전이 그대로일 때만 저장한다 (CAS)</li>
 *   <li>이벤트를 보내고 턴 타이머를 다시 건다</li>
 * </ol>
 */
@Service
public class GameService {

    public record PlayRequest(String actionId, String cardInstanceId, String targetId, Long expectedVersion) {
    }

    public record DiscardRequest(String actionId, String cardInstanceId, Long expectedVersion) {
    }

    private static final int MAX_ACTIONS_PER_SECOND = 5;
    /** 스냅샷용으로 읽는 최근 이벤트 수. 개인 이벤트가 섞여 있어 엔진이 담는 개수보다 넉넉히 읽는다 */
    private static final int SNAPSHOT_HISTORY = GameEngine.RECENT_EVENT_LIMIT * 2;

    private final GameRepository games;
    private final PackCatalog packs;
    private final GamePublisher publisher;
    private final TurnTimers timers;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher appEvents;
    private final SecureRandom seeds = new SecureRandom();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public GameService(GameRepository games, PackCatalog packs, GamePublisher publisher, TurnTimers timers,
                       StringRedisTemplate redis, ApplicationEventPublisher appEvents) {
        this.games = games;
        this.packs = packs;
        this.publisher = publisher;
        this.timers = timers;
        this.redis = redis;
        this.appEvents = appEvents;
    }

    /** 방에서 게임을 만든다. 좌석·손패는 엔진이 시드로 정한다 */
    public String createGame(Room room) {
        CardPack pack = packs.latestPack(room.getSettings().packCode());
        String gameId = "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<PlayerSeed> players = room.getMembers().stream()
                .map(m -> new PlayerSeed(m.getPlayerId(), m.getNickname()))
                .toList();
        GameEngine.StartResult start = new GameEngine(pack)
                .start(gameId, room.getSettings().toGameSettings(), players, seeds.nextLong());
        if (!games.create(start.state(), room.getRoomId())) {
            throw new IllegalStateException("게임을 저장하지 못했습니다: " + gameId);
        }
        games.appendEvents(gameId, start.events());
        publisher.publish(gameId, start.events());
        timers.schedule(start.state());
        return gameId;
    }

    public void play(String gameId, String playerId, PlayRequest request) {
        if (!allowAction(gameId, playerId, request.actionId())) {
            return;
        }
        act(gameId, playerId, (state, engine) -> engine.play(state,
                new PlayCommand(playerId, request.cardInstanceId(), request.targetId(), request.expectedVersion())));
    }

    public void discard(String gameId, String playerId, DiscardRequest request) {
        if (!allowAction(gameId, playerId, request.actionId())) {
            return;
        }
        act(gameId, playerId, (state, engine) -> engine.discard(state,
                new DiscardCommand(playerId, request.cardInstanceId(), request.expectedVersion())));
    }

    /** 턴 타이머가 부른다. 그 사이에 플레이어가 행동했으면(마감 시각이 바뀌었으면) 무시한다 */
    public void timeout(String gameId) {
        withLock(gameId, () -> {
            Optional<GameState> loaded = games.load(gameId);
            if (loaded.isEmpty()) {
                return;
            }
            GameState state = loaded.get();
            if (!state.inProgress()) {
                return;
            }
            if (state.getTurnDeadlineEpochMs() > System.currentTimeMillis()) {
                timers.schedule(state);
                return;
            }
            long before = state.getVersion();
            ActionResult result = engineFor(state).timeout(state);
            commit(gameId, null, before, state, result);
        });
    }

    /**
     * 새로고침·재접속 시 내 시점의 전체 상태를 보낸다 (FR-SYNC-01).
     * 행동 처리와 같은 락 안에서 읽고 보내야, 스냅샷보다 먼저 보낸 이벤트는 스냅샷에 포함되고
     * 나중 이벤트는 스냅샷 뒤에 도착한다 (클라이언트는 seq ≤ lastSeq 인 이벤트를 버린다).
     */
    public void sync(String gameId, String playerId) {
        withLock(gameId, () -> {
            GameState state = games.load(gameId)
                    .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "게임을 찾을 수 없습니다"));
            if (state.player(playerId) == null) {
                publisher.reject(gameId, playerId, "PLAYER_NOT_FOUND", null, "이 게임의 참가자가 아닙니다");
                return;
            }
            publisher.sendToPlayer(gameId, playerId, "SNAPSHOT",
                    engineFor(state).snapshot(state, playerId, games.recentEvents(gameId, SNAPSHOT_HISTORY)));
        });
    }

    // ------------------------------------------------------------------

    private void act(String gameId, String playerId, EngineAction action) {
        withLock(gameId, () -> {
            Optional<GameState> loaded = games.load(gameId);
            if (loaded.isEmpty()) {
                publisher.reject(gameId, playerId, "GAME_NOT_FOUND", null, "게임을 찾을 수 없습니다");
                return;
            }
            GameState state = loaded.get();
            long before = state.getVersion();
            ActionResult result = action.apply(state, engineFor(state));
            commit(gameId, playerId, before, state, result);
        });
    }

    private void commit(String gameId, String playerId, long versionBefore, GameState state, ActionResult result) {
        if (!result.accepted()) {
            if (playerId != null) {
                Rejection r = result.rejection();
                publisher.reject(gameId, playerId, r.code().name(), r.reason() == null ? null : r.reason().name(),
                        r.message());
            }
            return;
        }
        if (!games.saveIfVersion(state, versionBefore)) {
            if (playerId != null) {
                publisher.reject(gameId, playerId, "STALE_VERSION", null, "다른 행동이 먼저 처리됐습니다. 다시 불러옵니다");
            }
            return;
        }
        games.appendEvents(gameId, result.events());
        publisher.publish(gameId, result.events());
        timers.schedule(state);
        if (!state.inProgress()) {
            appEvents.publishEvent(new GameFinishedEvent(games.roomOf(gameId), gameId));
        }
    }

    /** 초당 행동 수 제한 + 같은 actionId 재전송 무시 (PRD 9.5, 11장) */
    private boolean allowAction(String gameId, String playerId, String actionId) {
        String rateKey = "ratelimit:" + playerId + ":" + (System.currentTimeMillis() / 1000);
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redis.expire(rateKey, Duration.ofSeconds(2));
        }
        if (count != null && count > MAX_ACTIONS_PER_SECOND) {
            publisher.reject(gameId, playerId, "RATE_LIMITED", null, "너무 빠르게 요청하고 있습니다");
            return false;
        }
        if (actionId != null && !actionId.isBlank()) {
            Boolean fresh = redis.opsForValue()
                    .setIfAbsent("action:" + gameId + ":" + actionId, "1", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(fresh)) {
                publisher.reject(gameId, playerId, "DUPLICATE_ACTION", null, "이미 처리한 요청입니다");
                return false;
            }
        }
        return true;
    }

    private GameEngine engineFor(GameState state) {
        return new GameEngine(packs.get(state.getPackCode(), state.getPackVersion()));
    }

    private void withLock(String gameId, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(gameId, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface EngineAction {
        ActionResult apply(GameState state, GameEngine engine);
    }
}
