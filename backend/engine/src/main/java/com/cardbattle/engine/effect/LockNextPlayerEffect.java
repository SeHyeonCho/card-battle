package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TargetResolver;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.Set;

/**
 * 다음 차례의 플레이어는 아무것도 할 수 없다 (시간아 멈춰라!).
 * 그 사람의 차례가 오면 카드 없이 곧바로 정산된다: 남은 누적 데미지를 받고 차례가 넘어간다.
 * <pre>{ "type": "LOCK_NEXT_PLAYER" }</pre>
 */
public final class LockNextPlayerEffect implements EffectHandler {

    @Override
    public String type() {
        return "LOCK_NEXT_PLAYER";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        PlayerState next = TargetResolver.nextAlive(ctx.state(), ctx.actor().getSeat(), ctx.state().getDirection());
        if (next != null) {
            ctx.state().setLockedPlayerId(next.getPlayerId());
        }
    }
}
