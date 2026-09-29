package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 조건을 만족하면 이 카드의 공격력을 바꾼다 (간지폭풍, 왼팔의 흑염룡, 사격, 냥냥펀치).
 * <pre>{ "type": "CONDITIONAL_ATTACK", "condition": { "type": "SELF_CURSED" }, "set": 60 }</pre>
 * set(공격력을 이 값으로) 또는 add(더하기) 중 하나를 쓴다.
 *
 * <p>공격력은 CARD_PLAYED 이벤트보다 먼저 정해져야 하므로, 실제 계산은
 * {@link com.cardbattle.engine.GameEngine}의 공격력 계산 단계에서 하고 apply는 아무것도 하지 않는다.
 */
public final class ConditionalAttackEffect implements EffectHandler {

    @Override
    public String type() {
        return "CONDITIONAL_ATTACK";
    }

    @Override
    public Set<String> params() {
        return Set.of("condition", "set", "add");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        // 공격력 계산 단계에서 이미 반영했다
    }

    /** 조건이 맞을 때 바뀐 공격력 */
    public static int adjust(int attack, EffectSpec spec) {
        if (spec.integer("set") != null) {
            return spec.integer("set");
        }
        return attack + spec.integer("add", 0);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (spec.raw("condition") == null) {
            errors.add(path + ".condition: 조건이 필요합니다");
        } else {
            errors.addAll(check.condition(spec.raw("condition"), path + ".condition"));
        }
        boolean hasSet = spec.raw("set") != null;
        boolean hasAdd = spec.raw("add") != null;
        if (hasSet == hasAdd) {
            errors.add(path + ": set 또는 add 중 하나만 있어야 합니다");
        } else {
            Validations.requirePositiveInt(spec, hasSet ? "set" : "add", path, errors);
        }
        return errors;
    }
}
