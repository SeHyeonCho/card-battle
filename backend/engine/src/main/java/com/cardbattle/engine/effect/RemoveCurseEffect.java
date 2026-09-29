package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 저주를 푼다 (대천사, 교황의 힘, 기적, 확률뚫기).
 * <pre>
 * { "type": "REMOVE_CURSE", "scope": "SELF" }                       내 저주
 * { "type": "REMOVE_CURSE", "scope": "ALL", "chanceEach": 0.5 }     모두, 각자 50% 확률
 * </pre>
 * scope는 효과 대상 이름(SELF, CHOSEN, ALL, ALL_OTHERS …)을 그대로 쓴다.
 */
public final class RemoveCurseEffect implements EffectHandler {

    @Override
    public String type() {
        return "REMOVE_CURSE";
    }

    @Override
    public Set<String> params() {
        return Set.of("scope", "chanceEach");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        Double chance = spec.raw("chanceEach") instanceof Number n ? n.doubleValue() : null;
        for (PlayerState target : ctx.resolveTargets(spec.str("scope"))) {
            if (target.cursed() && (chance == null || Validations.roll(ctx, chance))) {
                ctx.removeCurse(target, "EFFECT");
            }
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "scope", path, errors);
        if (spec.raw("chanceEach") != null) {
            Validations.requireProbability(spec, "chanceEach", path, errors);
        }
        return errors;
    }
}
