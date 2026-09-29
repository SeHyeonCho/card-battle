package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상마다 체력을 amount만큼 깎고, 깎은 합만큼 낸 사람이 회복한다 (생존왕, 찰지구나).
 * <pre>{ "type": "DRAIN", "target": "ALL_OTHERS", "amount": 15 }</pre>
 */
public final class DrainEffect implements EffectHandler {

    @Override
    public String type() {
        return "DRAIN";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "amount");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int amount = spec.integer("amount", 0);
        int total = 0;
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            if (target == ctx.actor()) {
                continue;
            }
            ctx.damage(target, amount, "DRAIN");
            total += amount;
        }
        ctx.heal(ctx.actor(), total, "DRAIN");
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        Validations.requirePositiveInt(spec, "amount", path, errors);
        return errors;
    }
}
