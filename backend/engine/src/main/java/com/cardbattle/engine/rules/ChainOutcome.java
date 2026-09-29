package com.cardbattle.engine.rules;

/**
 * 한 턴의 누적 판정 결과 (PRD 7.4).
 */
public enum ChainOutcome {
    /** A = 0 이거나 p ≥ A 인 공격 카드: D += p, A = p. 수령 없이 체인이 이어진다 */
    CONTINUE,
    /** p < A 인 공격 카드: 현재 D를 받고, 정산 후 A = p, D = p 로 새 체인 시작 (D8) */
    RECEIVE_AND_RESTART,
    /** 공격이 아닌 카드 또는 버리기: 남은 D를 받고 체인 종료 (A = 0, D = 0) */
    RECEIVE_AND_END,
    /** 데미지 전달 (다음 차례로): 받지 않고 A·D가 다음 플레이어에게 넘어감 */
    PASS_ON,
    /** 효과가 체인을 끝냄 (예: 즉시 전달). 행동한 사람은 받지 않는다 */
    ENDED_BY_EFFECT;

    public boolean receives() {
        return this == RECEIVE_AND_RESTART || this == RECEIVE_AND_END;
    }
}
