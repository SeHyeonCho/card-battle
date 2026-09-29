package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 확률 p로 then, 아니면 else 효과를 실행한다 (슈뢰딩거 캣).
 * <pre>{ "type": "CHANCE", "p": 0.5, "then": [ ... ], "else": [ ... ] }</pre>
 */
public final class ChanceEffect implements EffectHandler, NestedEffects {

    private EffectRegistry registry;

    @Override
    public void bind(EffectRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String type() {
        return "CHANCE";
    }

    @Override
    public Set<String> params() {
        return Set.of("p", "then", "else");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        double p = ((Number) spec.raw("p")).doubleValue();
        String branch = Validations.roll(ctx, p) ? "then" : "else";
        if (spec.raw(branch) != null) {
            registry.run(ctx, PackParser.effects(spec.raw(branch), type() + "." + branch));
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireProbability(spec, "p", path, errors);
        if (spec.raw("then") == null && spec.raw("else") == null) {
            errors.add(path + ": then 또는 else 중 하나는 있어야 합니다");
        }
        for (String branch : List.of("then", "else")) {
            if (spec.raw(branch) != null) {
                errors.addAll(check.effects(spec.raw(branch), path + "." + branch));
            }
        }
        return errors;
    }
}
