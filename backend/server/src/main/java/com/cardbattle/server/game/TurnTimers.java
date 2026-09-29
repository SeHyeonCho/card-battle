package com.cardbattle.server.game;

import com.cardbattle.engine.state.GameState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 턴 마감 시각을 Redis ZSET에 넣어 둔다 (PRD 4장 턴 타이머).
 * 서버가 재시작돼도 타이머가 사라지지 않는다.
 */
@Component
public class TurnTimers {

    private static final String KEY = "timers:turn";

    private final StringRedisTemplate redis;

    public TurnTimers(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void schedule(GameState state) {
        if (state.inProgress()) {
            redis.opsForZSet().add(KEY, state.getGameId(), state.getTurnDeadlineEpochMs());
        } else {
            redis.opsForZSet().remove(KEY, state.getGameId());
        }
    }

    /** 마감이 지난 게임을 꺼낸다. ZREM에 성공한 것만 가져가므로 서버가 여러 대여도 한 번만 처리된다 */
    public List<String> claimDue(long nowEpochMs) {
        Set<String> due = redis.opsForZSet().rangeByScore(KEY, 0, nowEpochMs);
        if (due == null || due.isEmpty()) {
            return List.of();
        }
        List<String> claimed = new ArrayList<>();
        for (String gameId : due) {
            Long removed = redis.opsForZSet().remove(KEY, gameId);
            if (removed != null && removed > 0) {
                claimed.add(gameId);
            }
        }
        return claimed;
    }
}
