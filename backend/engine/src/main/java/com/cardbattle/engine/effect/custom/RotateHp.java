package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.effect.PackCheck;
import com.cardbattle.engine.rules.TargetResolver;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 본진이 바뀐다: 모든 생존자가 시계(+1) 또는 반시계(-1) 방향 옆 사람의 체력을 받는다.
 * <pre>{ "type": "CUSTOM", "handler": "ROTATE_HP", "direction": "RANDOM" }</pre>
 * direction: CLOCKWISE / COUNTERCLOCKWISE / RANDOM
 */
public final class RotateHp implements CustomEffect.Handler {

    private static final Set<String> DIRECTIONS = Set.of("CLOCKWISE", "COUNTERCLOCKWISE", "RANDOM");

    @Override
    public String name() {
        return "ROTATE_HP";
    }

    @Override
    public Set<String> params() {
        return Set.of("direction");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        String dir = spec.str("direction", "RANDOM");
        int step = switch (dir) {
            case "CLOCKWISE" -> 1;
            case "COUNTERCLOCKWISE" -> -1;
            default -> ctx.random(2) == 0 ? 1 : -1;
        };
        Map<PlayerState, Integer> next = new LinkedHashMap<>();
        for (PlayerState p : ctx.state().alivePlayers()) {
            PlayerState neighbor = TargetResolver.nextAlive(ctx.state(), p.getSeat(), step);
            next.put(p, neighbor == null ? p.getHp() : neighbor.getHp());
        }
        next.forEach((p, hp) -> ctx.setHp(p, hp, "ROTATE"));
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (spec.raw("direction") != null && !DIRECTIONS.contains(spec.str("direction"))) {
            errors.add(path + ".direction: " + DIRECTIONS + " 중 하나여야 합니다");
        }
        return errors;
    }
}
