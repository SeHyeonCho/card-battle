package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 차례 순서를 반대로 돌린다. 다음 차례는 이전 플레이어가 된다 (유턴).
 * <pre>{ "type": "REVERSE_ORDER" }</pre>
 */
public final class ReverseOrderEffect implements EffectHandler {

    @Override
    public String type() {
        return "REVERSE_ORDER";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.state().setDirection(-ctx.state().getDirection());
        ctx.events().toAll(EventType.DIRECTION_CHANGED, payload("direction", ctx.state().getDirection()));
    }
}
