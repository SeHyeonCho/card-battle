package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.CurseState;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 나에게 걸린 저주를 그 저주를 건 사람에게 돌려준다 (이 차는 이제 제 겁니다).
 * 건 사람이 이미 탈락했거나 내가 나에게 건 저주면 아무 일도 없다.
 * <pre>{ "type": "REFLECT_CURSE", "scope": "SELF" }</pre>
 */
public final class ReflectCurseEffect implements EffectHandler {

    @Override
    public String type() {
        return "REFLECT_CURSE";
    }

    @Override
    public Set<String> params() {
        return Set.of("scope");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        for (PlayerState target : ctx.resolveTargets(spec.str("scope"))) {
            if (!target.cursed()) {
                continue;
            }
            CurseState curse = target.getCurse();
            PlayerState caster = ctx.state().player(curse.getCasterId());
            if (caster == null || !caster.alive() || caster == target) {
                continue;
            }
            ctx.removeCurse(target, "REFLECTED");
            ctx.applyCurse(caster, new CurseState(curse.getCurseId(), curse.getCardId(), target.getPlayerId(),
                    curse.getDef()));
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "scope", path, errors);
        return errors;
    }
}
