package com.cardbattle.engine.card;

/** 카드 대분류 (PRD 8.1). */
public enum CardCategory {
    /** 공격력으로 누적 데미지를 올리며 넘기는 카드 */
    ATTACK,
    /** 누적 데미지를 줄이거나 넘기는 카드 */
    SUPPORT,
    /** 누적 데미지와 무관한 이득 카드 */
    BENEFIT,
    /** 대상에게 거는 지속 디버프 (Phase 2) */
    CURSE,
    /** 그 밖의 카드 */
    MISC
}
