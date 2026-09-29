package com.cardbattle.engine;

/**
 * 한 판의 규칙 설정 (PRD 6.2 FR-ROOM-01).
 *
 * @param turnTimeSeconds 턴 제한 시간. 엔진은 마감 시각만 계산하고, 실제 타이머는 서버가 돌린다
 * @param ruleMode        "SC1" (기본). SC2는 Phase 4에서 추가 검토
 */
public record GameSettings(int startingHp, int hpCap, int handSize, int turnTimeSeconds, String ruleMode) {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;

    public GameSettings {
        if (ruleMode == null || ruleMode.isBlank()) {
            ruleMode = "SC1";
        }
        if (startingHp < 1) {
            throw new IllegalArgumentException("startingHp must be >= 1");
        }
        if (hpCap < startingHp) {
            throw new IllegalArgumentException("hpCap must be >= startingHp");
        }
        if (handSize < 1) {
            throw new IllegalArgumentException("handSize must be >= 1");
        }
        if (turnTimeSeconds < 1) {
            throw new IllegalArgumentException("turnTimeSeconds must be >= 1");
        }
    }

    public static GameSettings defaults() {
        return new GameSettings(200, 500, 5, 25, "SC1");
    }
}
