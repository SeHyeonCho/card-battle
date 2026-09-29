package com.cardbattle.engine.result;

/**
 * 행동 거절 사유 (PRD 9.5).
 * DUPLICATE_ACTION, RATE_LIMITED는 서버가 판단한다.
 */
public enum RejectCode {
    NOT_YOUR_TURN,
    CARD_NOT_IN_HAND,
    CARD_NOT_PLAYABLE,
    INVALID_TARGET,
    STALE_VERSION,
    GAME_FINISHED,
    PLAYER_NOT_FOUND
}
