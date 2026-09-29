package com.cardbattle.engine.state;

/**
 * 시드 기반 난수 (SplitMix64). 상태가 long 하나라 JSON 저장이 쉽고,
 * 같은 시드와 같은 입력이면 항상 같은 게임이 재현된다 (PRD 3.5, 11장 재현성).
 */
public final class GameRng {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private GameRng() {
    }

    public static long nextLong(GameState state) {
        long z = state.getRngState() + GOLDEN_GAMMA;
        state.setRngState(z);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, bound) 범위의 정수 */
    public static int nextInt(GameState state, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(state), bound);
    }

    /** [min, max] 범위의 정수 (양 끝 포함) */
    public static int between(GameState state, int min, int max) {
        return min + nextInt(state, max - min + 1);
    }
}
