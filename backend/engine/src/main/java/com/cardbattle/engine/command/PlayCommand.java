package com.cardbattle.engine.command;

/**
 * 카드 제출 요청.
 *
 * @param targetId        대상이 필요한 카드일 때 고른 플레이어 ID (없으면 null)
 * @param expectedVersion 클라이언트가 보고 있던 상태 버전. 다르면 STALE_VERSION (null이면 검사 안 함)
 */
public record PlayCommand(String playerId, String cardInstanceId, String targetId, Long expectedVersion) {
}
