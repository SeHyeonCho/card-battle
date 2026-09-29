package com.cardbattle.engine.result;

/** 카드 한 장을 지금 낼 수 있는지와, 낼 수 없다면 그 이유 (PRD FR-CARD-02). */
public record Playability(boolean playable, RejectCode code, PlayBlockReason reason, String message) {

    private static final Playability OK = new Playability(true, null, null, null);

    public static Playability ok() {
        return OK;
    }

    public static Playability blocked(PlayBlockReason reason, String message) {
        return new Playability(false, RejectCode.CARD_NOT_PLAYABLE, reason, message);
    }

    public static Playability invalidTarget(String message) {
        return new Playability(false, RejectCode.INVALID_TARGET, null, message);
    }

    public Rejection toRejection() {
        return new Rejection(code, reason, message);
    }
}
