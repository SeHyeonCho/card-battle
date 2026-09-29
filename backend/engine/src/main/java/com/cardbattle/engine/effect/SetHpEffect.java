package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상의 체력을 정해진 값으로 만든다 (존나좋군?, 파워 엘릭서).
 * <pre>{ "type": "SET_HP", "target": "ALL", "value": 100 }</pre>
 */
public final class SetHpEffect implements EffectHandler {

    @Override
    public String type() {
        return "SET_HP";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "value");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.setHp(target, spec.integer("value", 0), "EFFECT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        Validations.requirePositiveInt(spec, "value", path, errors);
        return errors;
    }
}
