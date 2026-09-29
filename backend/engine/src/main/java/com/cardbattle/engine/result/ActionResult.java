package com.cardbattle.engine.result;

import com.cardbattle.engine.event.GameEvent;

import java.util.List;

/**
 * 행동 처리 결과. 받아들여졌으면 상태가 바뀌고 이벤트가 생긴다.
 * 거절됐으면 상태는 그대로이고 rejection에 사유가 담긴다.
 */
public record ActionResult(boolean accepted, Rejection rejection, List<GameEvent> events) {

    public static ActionResult accepted(List<GameEvent> events) {
        return new ActionResult(true, null, List.copyOf(events));
    }

    public static ActionResult rejected(Rejection rejection) {
        return new ActionResult(false, rejection, List.of());
    }
}
