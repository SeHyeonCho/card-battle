package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상의 체력을 회복한다 (체력 상한까지).
 * <pre>{ "type": "HEAL", "target": "SELF", "amount": 25 }</pre>
 */
public final class HealEffect implements EffectHandler {

    @Override
    public String type() {
        return "HEAL";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "amount");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int amount = spec.integer("amount", 0);
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.heal(target, amount, "EFFECT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        Validations.requirePositiveInt(spec, "amount", path, errors);
        return errors;
    }
}
