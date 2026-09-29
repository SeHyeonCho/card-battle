package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Set;

/**
 * 다음 플레이어의 차례를 건너뛴다 (점프). 누적 데미지는 그다음 사람에게 간다.
 * <pre>{ "type": "SKIP_NEXT" }</pre>
 */
public final class SkipNextEffect implements EffectHandler {

    @Override
    public String type() {
        return "SKIP_NEXT";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.state().setPendingSkips(ctx.state().getPendingSkips() + 1);
    }
}
