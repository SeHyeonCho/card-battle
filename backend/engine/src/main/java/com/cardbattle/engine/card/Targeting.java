package com.cardbattle.engine.card;

/** 카드를 낼 때 대상을 고르는 방식 (PRD 8.4). */
public enum Targeting {
    /** 대상을 고르지 않는다 */
    NONE,
    /** 나를 제외한 생존자 중 한 명을 고른다 */
    CHOSEN_OTHER,
    /** 나를 포함한 생존자 중 한 명을 고른다 */
    CHOSEN_ANY;

    public boolean requiresChoice() {
        return this != NONE;
    }

    public boolean allowsSelf() {
        return this == CHOSEN_ANY;
    }
}
