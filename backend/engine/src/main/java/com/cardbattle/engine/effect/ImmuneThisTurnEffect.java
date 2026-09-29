package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Set;

/**
 * 이번 턴에 받아야 할 누적 데미지를 받지 않는다 (초능력자, 시간아 멈춰라!).
 * 체인 흐름(새 체인 시작·종료)은 그대로 진행되고 수령만 취소된다 (PRD 7.4).
 * <pre>{ "type": "IMMUNE_THIS_TURN" }</pre>
 */
public final class ImmuneThisTurnEffect implements EffectHandler {

    @Override
    public String type() {
        return "IMMUNE_THIS_TURN";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.grantImmunity();
    }
}
