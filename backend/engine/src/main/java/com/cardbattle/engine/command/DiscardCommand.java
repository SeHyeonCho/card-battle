package com.cardbattle.engine.command;

/** 카드 버리기 요청. */
public record DiscardCommand(String playerId, String cardInstanceId, Long expectedVersion) {
}
