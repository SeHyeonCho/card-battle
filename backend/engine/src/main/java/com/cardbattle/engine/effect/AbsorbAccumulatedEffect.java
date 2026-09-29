package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.ChainOutcome;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 누적 데미지를 받는 대신 체력으로 바꾸고 체인을 끝낸다 (먹방).
 * <pre>{ "type": "ABSORB_ACCUMULATED", "as": "HEAL", "ratio": 1 }</pre>
 */
public final class AbsorbAccumulatedEffect implements EffectHandler {

    @Override
    public String type() {
        return "ABSORB_ACCUMULATED";
    }

    @Override
    public Set<String> params() {
        return Set.of("as", "ratio");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        double ratio = spec.raw("ratio") instanceof Number n ? n.doubleValue() : 1.0;
        int amount = (int) Math.floor(ctx.state().getAccumulatedDamage() * ratio);
        ctx.heal(ctx.actor(), amount, "ABSORB");
        ctx.setChain(0, 0);
        ctx.decideOutcome(ChainOutcome.ENDED_BY_EFFECT);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (!"HEAL".equals(spec.str("as", "HEAL"))) {
            errors.add(path + ".as: 현재는 HEAL만 지원합니다");
        }
        if (spec.raw("ratio") != null && (!(spec.raw("ratio") instanceof Number n) || n.doubleValue() <= 0)) {
            errors.add(path + ".ratio: 0보다 큰 숫자여야 합니다");
        }
        return errors;
    }
}
