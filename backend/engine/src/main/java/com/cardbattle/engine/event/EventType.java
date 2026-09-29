package com.cardbattle.engine.event;

/** 게임 이벤트 종류 (PRD 9.4). Phase 2 이벤트는 해당 기능을 만들 때 추가한다. */
public enum EventType {
    // 공개 이벤트
    GAME_STARTED,
    TURN_STARTED,
    CARD_PLAYED,
    CARD_DISCARDED,
    TURN_TIMED_OUT,
    ACCUMULATION_CHANGED,
    HP_CHANGED,
    FIELD_CHANGED,
    HAND_COUNT_CHANGED,
    HAND_LIMIT_CHANGED,
    CURSE_APPLIED,
    CURSE_REMOVED,
    STATUS_APPLIED,
    STATUS_EXPIRED,
    HAND_REVEALED,
    /** 코스모의 강화기: 카드 내기가 버리기로 바뀜 */
    PLAY_FUMBLED,
    PLAYER_ELIMINATED,
    TURN_ENDED,
    GAME_ENDED,
    // 개인 이벤트
    HAND_UPDATED,
    PLAYABILITY_UPDATED
}
