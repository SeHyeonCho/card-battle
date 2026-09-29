package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 낸 사람과 대상의 체력을 맞바꾼다 (마인드 컨트롤).
 * <pre>{ "type": "SWAP_HP", "target": "CHOSEN" }</pre>
 */
public final class SwapHpEffect implements EffectHandler {

    @Override
    public String type() {
        return "SWAP_HP";
    }

    @Override
    public Set<String> params() {
        return Set.of("target");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        PlayerState self = ctx.actor();
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            if (target == self) {
                continue;
            }
            int mine = self.getHp();
            ctx.setHp(self, target.getHp(), "SWAP");
            ctx.setHp(target, mine, "SWAP");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        return errors;
    }
}
