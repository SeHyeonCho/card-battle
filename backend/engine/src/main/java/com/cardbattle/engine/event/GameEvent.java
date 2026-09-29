package com.cardbattle.engine.event;

import java.util.Map;

/**
 * 게임 이벤트 하나 (PRD 9.3).
 *
 * @param seq         게임 안에서의 일련번호 (재동기화 기준)
 * @param version     이 이벤트를 만든 행동이 끝난 뒤의 상태 버전
 * @param recipientId null이면 모두에게 보내는 공개 이벤트, 값이 있으면 그 플레이어 전용
 */
public record GameEvent(long seq, long version, EventType type, String recipientId, Map<String, Object> payload) {

    public boolean publicEvent() {
        return recipientId == null;
    }
}
