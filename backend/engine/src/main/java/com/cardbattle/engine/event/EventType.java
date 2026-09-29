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
    FIELD_LOCKS_CHANGED,
    TURN_SKIPPED,
    /** 시간아 멈춰라!: 이 차례는 아무것도 못 하고 누적을 받는다 */
    TURN_LOCKED,
    DIRECTION_CHANGED,
    TIME_BOMB_PLANTED,
    TIME_BOMB_EXPLODED,
    TIME_BOMB_REMOVED,
    DRAW_COUNTDOWN_CHANGED,
    /** 추가 제출 시작: 같은 플레이어가 한 장 더 낸다 */
    EXTRA_PLAY_STARTED,
    /** 블랙홀: 카드가 게임에서 제외됨 */
    CARDS_BANNED,
    PLAYER_ELIMINATED,
    /** 자리 비움 표시가 켜지거나 꺼짐 (연속 시간 초과, PRD 4.3) */
    PLAYER_AWAY_CHANGED,
    TURN_ENDED,
    GAME_ENDED,
    // 개인 이벤트
    HAND_UPDATED,
    PLAYABILITY_UPDATED
}
