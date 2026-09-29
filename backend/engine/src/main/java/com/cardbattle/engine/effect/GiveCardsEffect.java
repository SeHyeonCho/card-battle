package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 정해진 카드를 손패에 넣는다. 손패 한도를 넘어도 들어간다 (산타의 선물).
 * <pre>{ "type": "GIVE_CARDS", "target": "SELF", "cardIds": ["pack.card"] }</pre>
 */
public final class GiveCardsEffect implements EffectHandler {

    @Override
    public String type() {
        return "GIVE_CARDS";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "cardIds");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        List<?> ids = (List<?>) spec.raw("cardIds");
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ids.forEach(id -> target.getHand().add(ctx.newCard(String.valueOf(id))));
            ctx.handChanged(target);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        if (!(spec.raw("cardIds") instanceof List<?> ids) || ids.isEmpty()) {
            errors.add(path + ".cardIds: 카드 ID 배열이 필요합니다");
        }
        Validations.requireCardIds(spec, "cardIds", path, check, errors);
        return errors;
    }
}
