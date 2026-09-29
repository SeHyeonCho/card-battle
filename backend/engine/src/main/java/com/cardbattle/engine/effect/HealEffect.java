package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대상의 체력을 회복한다 (체력 상한까지).
 * <pre>
 * { "type": "HEAL", "target": "SELF", "amount": 25 }
 * { "type": "HEAL", "target": "SELF", "amountFrom": "CURRENT_ATTACK" }   현재 공격력만큼 (마시쪙)
 * </pre>
 * amountFrom: CURRENT_ATTACK(현재 공격력) / ACCUMULATED(누적 데미지)
 */
public final class HealEffect implements EffectHandler {

    @Override
    public String type() {
        return "HEAL";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "amount", "amountFrom");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int amount = switch (spec.str("amountFrom", "")) {
            case "CURRENT_ATTACK" -> ctx.state().getCurrentAttack();
            case "ACCUMULATED" -> ctx.state().getAccumulatedDamage();
            default -> spec.integer("amount", 0);
        };
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.heal(target, amount, "EFFECT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        if (spec.raw("amountFrom") != null) {
            if (spec.raw("amount") != null) {
                errors.add(path + ": amount와 amountFrom은 함께 쓸 수 없습니다");
            }
            if (!Set.of("CURRENT_ATTACK", "ACCUMULATED").contains(spec.str("amountFrom"))) {
                errors.add(path + ".amountFrom: CURRENT_ATTACK 또는 ACCUMULATED 여야 합니다");
            }
        } else {
            Validations.requirePositiveInt(spec, "amount", path, errors);
        }
        return errors;
    }
}
