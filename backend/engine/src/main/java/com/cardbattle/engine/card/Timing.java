package com.cardbattle.engine.card;

/** 효과가 발동하는 시점 (PRD 8.3). */
public enum Timing {
    /** 제출 즉시 */
    ON_PLAY,
    /** 이 카드를 낸 턴에 누적 데미지를 실제로 받았을 때 (새 체인이 시작된 뒤) */
    ON_RECEIVE,
    /** 이 카드를 낸 턴의 정산 시 */
    ON_TURN_END,
    /** 이 카드를 낸 사람의 다음 차례 시작 시 (Phase 2) */
    ON_NEXT_TURN
}
