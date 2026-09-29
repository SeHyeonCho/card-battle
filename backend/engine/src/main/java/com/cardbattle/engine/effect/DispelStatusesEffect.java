package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.Statuses;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.state.StatusState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 지속 상태를 없앤다 (이엠피 쇼크웨이브, 폭설).
 * <pre>{ "type": "DISPEL_STATUSES", "target": "ALL_OTHERS", "filter": { "statuses": ["UNTARGETABLE", "REGEN"] } }</pre>
 * filter를 생략하면 대상의 지속 상태를 모두 없앤다.
 */
public final class DispelStatusesEffect implements EffectHandler {

    @Override
    public String type() {
        return "DISPEL_STATUSES";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "filter");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        List<?> names = spec.raw("filter") instanceof Map<?, ?> f && f.get("statuses") instanceof List<?> l ? l : null;
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            for (StatusState st : List.copyOf(target.getStatuses())) {
                if (names == null || names.contains(st.getStatus())) {
                    ctx.removeStatus(target, st);
                }
            }
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        Object filter = spec.raw("filter");
        if (filter == null) {
            return errors;
        }
        if (!(filter instanceof Map<?, ?> f) || !(f.get("statuses") instanceof List<?> names) || f.size() != 1) {
            errors.add(path + ".filter: { \"statuses\": [...] } 형태여야 합니다");
            return errors;
        }
        for (Object n : names) {
            if (!Statuses.ALL.contains(String.valueOf(n))) {
                errors.add(path + ".filter.statuses: 알 수 없는 상태 '" + n + "' (지원: " + Statuses.ALL + ")");
            }
        }
        return errors;
    }
}
