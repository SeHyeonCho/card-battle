package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.effect.PackCheck;
import com.cardbattle.engine.rules.ChainOutcome;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 안아줘요: 누적 데미지를 생존자 모두가 나눠 받는다. (unit × 인원) 데미지마다 각자 unit씩 깎이고,
 * 나머지는 사라진다. 체인은 끝난다.
 * <pre>{ "type": "CUSTOM", "handler": "SHARE_ACCUMULATED", "unit": 5 }</pre>
 */
public final class ShareAccumulated implements CustomEffect.Handler {

    @Override
    public String name() {
        return "SHARE_ACCUMULATED";
    }

    @Override
    public Set<String> params() {
        return Set.of("unit");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int unit = spec.integer("unit", 5);
        List<PlayerState> alive = ctx.state().alivePlayers();
        int each = ctx.state().getAccumulatedDamage() / (unit * alive.size()) * unit;
        for (PlayerState p : alive) {
            ctx.damage(p, each, "SHARE");
        }
        ctx.setChain(0, 0);
        ctx.decideOutcome(ChainOutcome.ENDED_BY_EFFECT);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (!(spec.integer("unit") != null && spec.integer("unit") > 0)) {
            errors.add(path + ".unit: 1 이상의 정수가 필요합니다");
        }
        return errors;
    }
}
