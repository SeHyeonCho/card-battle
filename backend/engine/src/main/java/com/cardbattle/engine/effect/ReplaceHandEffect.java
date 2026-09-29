package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.Passives;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 손패를 버리고 새 카드로 바꾼다.
 * <pre>
 * { "type": "REPLACE_HAND", "target": "ALL" }                                          전부 새로 (밥상 뒤집기)
 * { "type": "REPLACE_HAND", "target": "CHOSEN", "resetHandLimit": true }               한도도 기본값으로 (고소)
 * { "type": "REPLACE_HAND", "target": "SELF", "count": 1, "position": "LEFTMOST" }     가장 왼쪽 한 장만 (힙통령)
 * { "type": "REPLACE_HAND", "target": "SELF", "cardIds": [...], "resetHandLimit": true }  정해진 카드로 (금수저)
 * </pre>
 */
public final class ReplaceHandEffect implements EffectHandler {

    @Override
    public String type() {
        return "REPLACE_HAND";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "count", "position", "cardIds", "resetHandLimit");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            if (spec.bool("resetHandLimit", false)) {
                ctx.setHandLimit(target, ctx.state().getSettings().handSize());
            }
            List<CardInstance> hand = target.getHand();
            if (spec.integer("count") != null) {
                int n = Math.min(spec.integer("count"), hand.size());
                for (int i = 0; i < n; i++) {
                    hand.remove("RIGHTMOST".equals(spec.str("position")) ? hand.size() - 1 : 0);
                    hand.add(ctx.drawRandom());
                }
            } else if (spec.raw("cardIds") instanceof List<?> ids) {
                hand.clear();
                ids.forEach(id -> hand.add(ctx.newCard(String.valueOf(id))));
            } else {
                hand.clear();
                while (hand.size() < Passives.handLimit(target)) {
                    hand.add(ctx.drawRandom());
                }
            }
            ctx.handChanged(target);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        if (spec.raw("count") != null) {
            Validations.requirePositiveInt(spec, "count", path, errors);
        }
        if (spec.raw("position") != null && !Set.of("LEFTMOST", "RIGHTMOST").contains(spec.str("position"))) {
            errors.add(path + ".position: LEFTMOST 또는 RIGHTMOST 여야 합니다");
        }
        if (spec.raw("count") != null && spec.raw("cardIds") != null) {
            errors.add(path + ": count와 cardIds는 함께 쓸 수 없습니다");
        }
        Validations.requireCardIds(spec, "cardIds", path, check, errors);
        return errors;
    }
}
