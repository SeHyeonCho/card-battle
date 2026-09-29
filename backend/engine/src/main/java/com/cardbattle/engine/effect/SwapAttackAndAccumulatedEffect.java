package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Set;

/**
 * 현재 공격력과 누적 데미지를 맞바꾼다 (갈!). 보통 TRANSFER_ACCUMULATED와 함께 쓴다.
 * <pre>{ "type": "SWAP_ATTACK_AND_ACCUMULATED" }</pre>
 */
public final class SwapAttackAndAccumulatedEffect implements EffectHandler {

    @Override
    public String type() {
        return "SWAP_ATTACK_AND_ACCUMULATED";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.setChain(ctx.state().getAccumulatedDamage(), ctx.state().getCurrentAttack());
    }
}
