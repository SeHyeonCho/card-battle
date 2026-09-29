package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 낸 사람과 대상의 손패 전체를 맞바꾼다 (약탈의 수행사제). includeHandLimit면 손패 한도도 바꾼다.
 * <pre>{ "type": "SWAP_HAND", "target": "CHOSEN", "includeHandLimit": true }</pre>
 */
public final class SwapHandEffect implements EffectHandler {

    @Override
    public String type() {
        return "SWAP_HAND";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "includeHandLimit");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        PlayerState self = ctx.actor();
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            if (target == self) {
                continue;
            }
            List<CardInstance> mine = new ArrayList<>(self.getHand());
            self.setHand(target.getHand());
            target.setHand(mine);
            if (spec.bool("includeHandLimit", false)) {
                int limit = self.getHandLimit();
                ctx.setHandLimit(self, target.getHandLimit());
                ctx.setHandLimit(target, limit);
            }
            ctx.handChanged(self);
            ctx.handChanged(target);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        return errors;
    }
}
