package com.cardbattle.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 0.5초마다 마감이 지난 턴을 찾아 시간 초과 처리한다 (FR-GAME-06). */
@Component
public class TurnTimerWorker {

    private static final Logger log = LoggerFactory.getLogger(TurnTimerWorker.class);

    private final TurnTimers timers;
    private final GameService games;

    public TurnTimerWorker(TurnTimers timers, GameService games) {
        this.timers = timers;
        this.games = games;
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        for (String gameId : timers.claimDue(System.currentTimeMillis())) {
            try {
                games.timeout(gameId);
            } catch (RuntimeException e) {
                log.error("턴 시간 초과 처리 실패: {}", gameId, e);
            }
        }
    }
}
