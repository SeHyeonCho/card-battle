package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 누적 데미지를 바꾼다.
 * <pre>{ "type": "MODIFY_ACCUMULATED", "op": "SUB", "value": 30 }</pre>
 * op: ADD(더하기) / SUB(빼기) / MUL(곱하기) / PERCENT_SUB(value% 만큼 빼기)
 */
public final class ModifyAccumulatedEffect implements EffectHandler {

    private static final Set<String> OPS = Set.of("ADD", "SUB", "MUL", "PERCENT_SUB");

    @Override
    public String type() {
        return "MODIFY_ACCUMULATED";
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        GameState state = ctx.state();
        int d = state.getAccumulatedDamage();
        int value = spec.integer("value", 0);
        int next = switch (spec.str("op")) {
            case "ADD" -> d + value;
            case "SUB" -> d - value;
            case "MUL" -> d * value;
            case "PERCENT_SUB" -> d - (d * value / 100);
            default -> throw new IllegalStateException("unknown op: " + spec.str("op"));
        };
        ctx.setChain(state.getCurrentAttack(), next);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path) {
        List<String> errors = new ArrayList<>();
        String op = spec.str("op");
        if (op == null || !OPS.contains(op)) {
            errors.add(path + ".op: ADD / SUB / MUL / PERCENT_SUB 중 하나여야 합니다");
        }
        if (!spec.integerParam("value") || spec.integer("value") < 0) {
            errors.add(path + ".value: 0 이상의 정수가 필요합니다");
        } else if ("PERCENT_SUB".equals(op) && spec.integer("value") > 100) {
            errors.add(path + ".value: PERCENT_SUB는 0~100이어야 합니다");
        }
        return errors;
    }
}
