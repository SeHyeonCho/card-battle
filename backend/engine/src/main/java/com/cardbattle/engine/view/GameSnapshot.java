package com.cardbattle.engine.view;

import com.cardbattle.engine.GameSettings;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.state.ExtraPlayState;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.FieldLock;
import com.cardbattle.engine.state.GameStatus;
import com.cardbattle.engine.state.TimeBomb;

import java.util.List;

/**
 * 특정 플레이어 시점의 전체 상태 (PRD FR-SYNC-01).
 * 재접속·새로고침 시 이것 하나로 화면을 다시 그린다. 다른 사람 손패는 들어가지 않는다.
 *
 * @param turnActiveFromEpochMs 차례 전환이 끝나 카드를 낼 수 있게 되는 시각 (FR-GAME-10)
 * @param transitionRemainingMs 스냅샷을 만든 시점에 남은 전환 시간. 화면은 시계 차이 없이 이 값으로 기다린다
 * @param recentEvents 최근 공개 이벤트 (오래된 것부터). 화면이 게임 로그를 다시 채우는 데 쓴다 (FR-UI-04)
 */
public record GameSnapshot(
        String gameId,
        long version,
        long lastSeq,
        GameSettings settings,
        GameStatus status,
        int turnNumber,
        String currentPlayerId,
        int direction,
        int currentAttack,
        int accumulatedDamage,
        List<FieldCard> field,
        List<FieldLock> fieldLocks,
        TimeBomb timeBomb,
        Integer drawCountdown,
        ExtraPlayState extraPlay,
        List<PlayerView> players,
        String viewerId,
        List<CardInstance> myHand,
        List<CardPlayabilityView> playability,
        long turnDeadlineEpochMs,
        long turnActiveFromEpochMs,
        long transitionRemainingMs,
        List<String> winnerIds,
        String packCode,
        int packVersion,
        List<CardView> cards,
        List<GameEvent> recentEvents) {
}
