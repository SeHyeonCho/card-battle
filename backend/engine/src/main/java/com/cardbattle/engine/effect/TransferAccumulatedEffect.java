package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.ChainOutcome;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;

/**
 * 누적 데미지를 다른 사람에게 넘긴다 (PRD 7.4 데미지 전달).
 * <pre>
 * 다음 차례로 넘기기: { "type": "TRANSFER_ACCUMULATED", "to": "NEXT", "multiplier": 1 }
 * 즉시 입히기:       { "type": "TRANSFER_ACCUMULATED", "to": "CHOSEN", "immediate": true }
 * </pre>
 * immediate가 false면 A·D(× multiplier)를 유지한 채 체인이 다음 플레이어로 간다 (to는 NEXT만 허용).
 * immediate가 true면 대상이 즉시 D(× multiplier)만큼 피해를 입고 체인이 끝난다.
 */
public final class TransferAccumulatedEffect implements EffectHandler {

    @Override
    public String type() {
        return "TRANSFER_ACCUMULATED";
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        GameState state = ctx.state();
        int amount = state.getAccumulatedDamage() * spec.integer("multiplier", 1);
        if (spec.bool("immediate", false)) {
            for (PlayerState target : ctx.resolveTargets(spec.str("to"))) {
                ctx.damage(target, amount, "TRANSFER");
            }
            ctx.setChain(0, 0);
            ctx.decideOutcome(ChainOutcome.ENDED_BY_EFFECT);
        } else {
            ctx.setChain(state.getCurrentAttack(), amount);
            ctx.decideOutcome(ChainOutcome.PASS_ON);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "to", path, errors);
        if (spec.integer("multiplier") != null && (!spec.integerParam("multiplier") || spec.integer("multiplier") < 1)) {
            errors.add(path + ".multiplier: 1 이상의 정수가 필요합니다");
        }
        if (!spec.bool("immediate", false) && !"NEXT".equals(spec.str("to"))) {
            errors.add(path + ".to: immediate가 false면 NEXT만 쓸 수 있습니다");
        }
        return errors;
    }
}
