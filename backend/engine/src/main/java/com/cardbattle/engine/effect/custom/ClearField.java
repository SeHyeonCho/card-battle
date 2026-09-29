package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.GameState;

import java.util.List;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 유성우: 필드의 카드를 모두 없앤다 (필드 락도 풀린다). 이 카드는 판정 뒤 새 필드가 된다.
 * <pre>{ "type": "CUSTOM", "handler": "CLEAR_FIELD" }</pre>
 */
public final class ClearField implements CustomEffect.Handler {

    @Override
    public String name() {
        return "CLEAR_FIELD";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        GameState state = ctx.state();
        if (!state.getField().isEmpty()) {
            state.setField(List.of());
            ctx.events().toAll(EventType.FIELD_CHANGED, payload("field", List.of()));
        }
        if (!state.getFieldLocks().isEmpty()) {
            state.setFieldLocks(List.of());
            ctx.events().toAll(EventType.FIELD_LOCKS_CHANGED, payload("locks", List.of()));
        }
    }
}
