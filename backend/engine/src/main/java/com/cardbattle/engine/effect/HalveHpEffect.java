package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상의 체력을 절반으로 만든다 (반갈죽). 홀수면 내림.
 * <pre>{ "type": "HALVE_HP", "target": "CHOSEN" }</pre>
 */
public final class HalveHpEffect implements EffectHandler {

    @Override
    public String type() {
        return "HALVE_HP";
    }

    @Override
    public Set<String> params() {
        return Set.of("target");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.damage(target, target.getHp() - target.getHp() / 2, "EFFECT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        return errors;
    }
}
