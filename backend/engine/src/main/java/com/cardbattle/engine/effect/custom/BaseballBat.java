package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.effect.PackCheck;
import com.cardbattle.engine.rules.PlayabilityChecker;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.FieldLock;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 야구빠따: (카드는 alwaysPlayable) 필드 락이나 저주 락 때문에 원래는 낼 수 없던 상황에서 내면,
 * 그 필드 카드를 낸 사람 또는 저주를 건 사람에게 damage만큼 피해를 준다.
 * <pre>{ "type": "CUSTOM", "handler": "BASEBALL_BAT", "damage": 45 }</pre>
 */
public final class BaseballBat implements CustomEffect.Handler {

    @Override
    public String name() {
        return "BASEBALL_BAT";
    }

    @Override
    public Set<String> params() {
        return Set.of("damage");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int damage = spec.integer("damage", 0);
        PlayerState actor = ctx.actor();
        FieldLock lock = PlayabilityChecker.fieldLock(ctx.state(), ctx.card());
        if (lock != null) {
            hit(ctx, ctx.state().player(lock.getOwnerId()), damage);
        }
        if (PlayabilityChecker.curseLocked(actor, ctx.card())) {
            hit(ctx, ctx.state().player(actor.getCurse().getCasterId()), damage);
        }
    }

    private static void hit(TurnContext ctx, PlayerState target, int damage) {
        if (target != null && target != ctx.actor()) {
            ctx.damage(target, damage, "BASEBALL_BAT");
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (!(spec.integer("damage") != null && spec.integer("damage") > 0)) {
            errors.add(path + ".damage: 1 이상의 정수가 필요합니다");
        }
        return errors;
    }
}
