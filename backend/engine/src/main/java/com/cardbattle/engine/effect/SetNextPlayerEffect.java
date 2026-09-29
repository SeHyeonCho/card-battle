package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 고른 사람이 다음 차례를 맡는다 (교차로). 그 뒤로는 원래 방향대로 이어진다.
 * <pre>{ "type": "SET_NEXT_PLAYER", "target": "CHOSEN" }</pre>
 */
public final class SetNextPlayerEffect implements EffectHandler {

    @Override
    public String type() {
        return "SET_NEXT_PLAYER";
    }

    @Override
    public Set<String> params() {
        return Set.of("target");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        List<PlayerState> targets = ctx.resolveTargets(spec.str("target"));
        if (!targets.isEmpty()) {
            ctx.state().setForcedNextPlayerId(targets.get(0).getPlayerId());
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        return errors;
    }
}
