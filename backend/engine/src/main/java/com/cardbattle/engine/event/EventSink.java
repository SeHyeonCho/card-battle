package com.cardbattle.engine.event;

import com.cardbattle.engine.state.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 행동 하나를 처리하는 동안 생긴 이벤트를 모은다.
 * commit()을 호출하면 상태 버전이 1 오른다.
 */
public final class EventSink {

    private final GameState state;
    private final long version;
    private final List<GameEvent> events = new ArrayList<>();

    public EventSink(GameState state) {
        this.state = state;
        this.version = state.getVersion() + 1;
    }

    public void toAll(EventType type, Map<String, Object> payload) {
        add(type, null, payload);
    }

    public void toPlayer(String playerId, EventType type, Map<String, Object> payload) {
        add(type, playerId, payload);
    }

    private void add(EventType type, String recipientId, Map<String, Object> payload) {
        long seq = state.getNextSeq();
        state.setNextSeq(seq + 1);
        events.add(new GameEvent(seq, version, type, recipientId,
                Collections.unmodifiableMap(new LinkedHashMap<>(payload))));
    }

    public List<GameEvent> commit() {
        state.setVersion(version);
        return List.copyOf(events);
    }

    /** null 값을 허용하는 Map 생성 도우미: payload("a", 1, "b", null) */
    public static Map<String, Object> payload(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
