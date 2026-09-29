package com.cardbattle.engine.result;

/** 거절된 행동의 사유. reason은 CARD_NOT_PLAYABLE일 때만 채워진다. */
public record Rejection(RejectCode code, PlayBlockReason reason, String message) {

    public static Rejection of(RejectCode code, String message) {
        return new Rejection(code, null, message);
    }
}
