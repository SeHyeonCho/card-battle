package com.cardbattle.engine.result;

/** CARD_NOT_PLAYABLE의 세부 사유 (PRD 7.3). */
public enum PlayBlockReason {
    /** 필드에 놓인 카드의 락 효과 (Phase 2) */
    FIELD_LOCK,
    /** 내게 걸린 저주의 락 효과 (Phase 2) */
    CURSE_LOCK,
    /** 카드의 제출 조건을 만족하지 않음 */
    CONDITION_UNMET,
    /** 추가 제출 중: 추가로 낼 수 있는 카드가 아님 (또는 버려야 하는 상태) */
    EXTRA_PLAY
}
