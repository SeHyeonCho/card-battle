package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.Statuses;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대상에게 지속 상태를 건다. 걸린 사람의 턴이 끝날 때마다 1씩 줄고(걸린 그 턴은 제외) 0이 되면 사라진다.
 * <pre>
 * { "type": "APPLY_STATUS", "target": "SELF", "status": "UNTARGETABLE", "turns": 3 }                       천상의 보호막
 * { "type": "APPLY_STATUS", "target": "SELF", "status": "REGEN", "turns": 4, "params": { "amount": 15 } }  황금사과
 * { "type": "APPLY_STATUS", "target": "SELF", "status": "NEXT_ATTACK_BONUS", "turns": 1,
 *   "params": { "value": 30 } }                                                                           스팀팩
 * </pre>
 */
public final class ApplyStatusEffect implements EffectHandler {

    @Override
    public String type() {
        return "APPLY_STATUS";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "status", "turns", "params");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(TurnContext ctx, EffectSpec spec) {
        Map<String, Object> params = spec.raw("params") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.applyStatus(target, spec.str("status"), spec.integer("turns", 1), params);
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        String status = spec.str("status");
        if (!Statuses.PLAYER.contains(String.valueOf(status))) {
            errors.add(path + ".status: 알 수 없는 상태 '" + status + "' (지원: " + Statuses.PLAYER + ")");
        }
        Validations.requirePositiveInt(spec, "turns", path, errors);
        Object params = spec.raw("params");
        if (params != null && !(params instanceof Map<?, ?>)) {
            errors.add(path + ".params: 객체여야 합니다");
        }
        String needed = Statuses.REGEN.equals(status) ? "amount"
                : Statuses.NEXT_ATTACK_BONUS.equals(status) ? "value" : null;
        if (needed != null && !(params instanceof Map<?, ?> m && m.get(needed) instanceof Integer v && v > 0)) {
            errors.add(path + ".params." + needed + ": 1 이상의 정수가 필요합니다");
        }
        return errors;
    }
}
