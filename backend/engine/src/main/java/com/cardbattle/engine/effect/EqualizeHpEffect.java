package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상들의 체력을 낸 사람의 체력과 같게 만든다 (의리).
 * <pre>{ "type": "EQUALIZE_HP", "target": "ALL_OTHERS" }</pre>
 */
public final class EqualizeHpEffect implements EffectHandler {

    @Override
    public String type() {
        return "EQUALIZE_HP";
    }

    @Override
    public Set<String> params() {
        return Set.of("target");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int hp = ctx.actor().getHp();
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.setHp(target, hp, "EFFECT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        return errors;
    }
}
