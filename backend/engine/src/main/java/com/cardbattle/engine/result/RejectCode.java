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
    PLAYER_NOT_FOUND,
    /** 차례 전환 연출 중이라 아직 낼 수 없다 (FR-GAME-10) */
    TURN_TRANSITION,
    /** 자리 비움 상태가 아닌 플레이어는 강퇴할 수 없다 */
    PLAYER_NOT_AWAY
}
