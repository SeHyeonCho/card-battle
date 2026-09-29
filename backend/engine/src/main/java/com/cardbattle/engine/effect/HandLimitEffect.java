package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 손패 한도를 바꾼다 (카드부자). 최소 1장. 늘어난 만큼은 그 사람의 턴 정산 드로우 때 채워진다.
 * <pre>{ "type": "HAND_LIMIT", "target": "SELF", "op": "ADD", "value": 1 }</pre>
 */
public final class HandLimitEffect implements EffectHandler {

    @Override
    public String type() {
        return "HAND_LIMIT";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "op", "value");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int value = spec.integer("value", 0);
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            int next = "SET".equals(spec.str("op")) ? value : target.getHandLimit() + value;
            ctx.setHandLimit(target, next);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        if (!Set.of("ADD", "SET").contains(spec.str("op", ""))) {
            errors.add(path + ".op: ADD 또는 SET 이어야 합니다");
        }
        if (!spec.integerParam("value")) {
            errors.add(path + ".value: 정수가 필요합니다");
        }
        return errors;
    }
}
