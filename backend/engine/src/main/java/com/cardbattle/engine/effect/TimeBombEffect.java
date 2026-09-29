package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.TimeBomb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 랜덤시한폭탄을 설치한다. 설치한 턴부터 매 턴 정산 마지막에 확률 p로 터지고,
 * 터지면 그 턴에 행동한 사람이 damage만큼 피해를 받는다. 그 턴에 pByTag의 태그 카드를 냈으면 확률이 바뀐다.
 * <pre>{ "type": "TIME_BOMB", "p": 0.125, "damage": 50, "pByTag": { "FIRE": 0.25, "ELECTRIC": 0.25 } }</pre>
 */
public final class TimeBombEffect implements EffectHandler {

    @Override
    public String type() {
        return "TIME_BOMB";
    }

    @Override
    public Set<String> params() {
        return Set.of("p", "damage", "pByTag");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        Map<String, Double> byTag = new LinkedHashMap<>();
        if (spec.raw("pByTag") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> byTag.put(String.valueOf(k), ((Number) v).doubleValue()));
        }
        TimeBomb bomb = new TimeBomb(ctx.actor().getPlayerId(), ((Number) spec.raw("p")).doubleValue(),
                spec.integer("damage", 0), byTag);
        ctx.state().setTimeBomb(bomb);
        ctx.events().toAll(EventType.TIME_BOMB_PLANTED, payload(
                "ownerId", bomb.getOwnerId(), "p", bomb.getP(), "damage", bomb.getDamage()));
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireProbability(spec, "p", path, errors);
        Validations.requirePositiveInt(spec, "damage", path, errors);
        Object byTag = spec.raw("pByTag");
        if (byTag != null) {
            if (!(byTag instanceof Map<?, ?> m)) {
                errors.add(path + ".pByTag: { 태그: 확률 } 객체여야 합니다");
            } else {
                m.forEach((k, v) -> {
                    if (!(v instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 1) {
                        errors.add(path + ".pByTag." + k + ": 0~1 사이의 확률이 필요합니다");
                    }
                });
            }
        }
        return errors;
    }
}
