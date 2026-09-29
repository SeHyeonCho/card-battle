package com.cardbattle.server.game;

import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.server.common.Json;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 진행 중인 게임 상태를 Redis에 저장한다 (PRD 10.2).
 * 저장은 Lua 스크립트로 "버전이 예상과 같을 때만" 원자적으로 한다 (CAS).
 */
@Repository
public class GameRepository {

    private static final Duration TTL = Duration.ofHours(12);

    /**
     * KEYS[1] = 상태 키, KEYS[2] = 버전 키
     * ARGV[1] = 예상 버전 (-1이면 새로 만들기), ARGV[2] = 새 버전, ARGV[3] = 상태 JSON, ARGV[4] = TTL(초)
     */
    private static final RedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if (current == false and ARGV[1] == '-1') or current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[4])
              redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[4])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Json json;

    public GameRepository(StringRedisTemplate redis, Json json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<GameState> load(String gameId) {
        String value = redis.opsForValue().get(stateKey(gameId));
        return value == null ? Optional.empty() : Optional.of(json.read(value, GameState.class));
    }

    public boolean create(GameState state, String roomId) {
        boolean created = compareAndSet(state, -1);
        if (created) {
            redis.opsForValue().set(roomKey(state.getGameId()), roomId, TTL);
        }
        return created;
    }

    /** 저장된 버전이 expectedVersion일 때만 저장한다. 다른 요청이 먼저 바꿨으면 false */
    public boolean saveIfVersion(GameState state, long expectedVersion) {
        return compareAndSet(state, expectedVersion);
    }

    public String roomOf(String gameId) {
        return redis.opsForValue().get(roomKey(gameId));
    }

    /** 이벤트 기록 (재동기화·리플레이용). Phase 3에서 seq 기반 재전송에 쓴다 */
    public void appendEvents(String gameId, List<GameEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        String key = eventsKey(gameId);
        redis.opsForList().rightPushAll(key, events.stream().map(json::write).toList());
        redis.expire(key, TTL);
    }

    /** 가장 최근 이벤트 count개 (오래된 것부터). 개인 이벤트도 섞여 있으니 걸러서 써야 한다 */
    public List<GameEvent> recentEvents(String gameId, int count) {
        List<String> raw = redis.opsForList().range(eventsKey(gameId), -count, -1);
        return raw == null ? List.of() : raw.stream().map(value -> json.read(value, GameEvent.class)).toList();
    }

    private boolean compareAndSet(GameState state, long expectedVersion) {
        Long result = redis.execute(COMPARE_AND_SET,
                List.of(stateKey(state.getGameId()), versionKey(state.getGameId())),
                String.valueOf(expectedVersion),
                String.valueOf(state.getVersion()),
                json.write(state),
                String.valueOf(TTL.toSeconds()));
        return result != null && result == 1L;
    }

    private static String stateKey(String gameId) {
        return "game:" + gameId + ":state";
    }

    private static String eventsKey(String gameId) {
        return "game:" + gameId + ":events";
    }

    private static String versionKey(String gameId) {
        return "game:" + gameId + ":version";
    }

    private static String roomKey(String gameId) {
        return "game:" + gameId + ":room";
    }
}
