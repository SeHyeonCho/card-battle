package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Set;

/**
 * 누적 데미지를 0으로 만든다.
 * <pre>{ "type": "RESET_ACCUMULATED" }</pre>
 * 체인 종료(A = 0)는 이후 누적 판정이 처리한다.
 */
public final class ResetAccumulatedEffect implements EffectHandler {

    @Override
    public String type() {
        return "RESET_ACCUMULATED";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.setChain(ctx.state().getCurrentAttack(), 0);
    }
}
