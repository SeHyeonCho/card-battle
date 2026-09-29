package com.cardbattle.engine.view;

import com.cardbattle.engine.GameSettings;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameStatus;

import java.util.List;

/**
 * 특정 플레이어 시점의 전체 상태 (PRD FR-SYNC-01).
 * 재접속·새로고침 시 이것 하나로 화면을 다시 그린다. 다른 사람 손패는 들어가지 않는다.
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
        List<PlayerView> players,
        String viewerId,
        List<CardInstance> myHand,
        List<CardPlayabilityView> playability,
        long turnDeadlineEpochMs,
        List<String> winnerIds,
        String packCode,
        int packVersion,
        List<CardView> cards) {
}
