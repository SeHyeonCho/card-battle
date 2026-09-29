package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상의 체력을 깎는다. 반동 데미지는 target을 SELF로 쓴다.
 * <pre>{ "type": "DAMAGE", "target": "ALL_OTHERS", "amount": 10 }</pre>
 */
public final class DamageEffect implements EffectHandler {

    @Override
    public String type() {
        return "DAMAGE";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "amount");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int amount = spec.integer("amount", 0);
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.damage(target, amount, "EFFECT");
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
