package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 가중치 표에서 하나를 뽑아 그 효과를 실행한다 (로켓 프레시, 러시안 룰렛, 산타의 선물).
 * <pre>{ "type": "RANDOM_CHOICE", "table": [ { "weight": 1, "effects": [ ... ] }, ... ] }</pre>
 */
public final class RandomChoiceEffect implements EffectHandler, NestedEffects {

    private EffectRegistry registry;

    @Override
    public void bind(EffectRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String type() {
        return "RANDOM_CHOICE";
    }

    @Override
    public Set<String> params() {
        return Set.of("table");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        List<?> table = (List<?>) spec.raw("table");
        int total = 0;
        for (Object row : table) {
            total += weight(row);
        }
        int roll = ctx.random(total);
        for (int i = 0; i < table.size(); i++) {
            roll -= weight(table.get(i));
            if (roll < 0) {
                Object effects = ((Map<?, ?>) table.get(i)).get("effects");
                registry.run(ctx, PackParser.effects(effects, type() + ".table[" + i + "].effects"));
                return;
            }
        }
    }

    private static int weight(Object row) {
        return ((Number) ((Map<?, ?>) row).get("weight")).intValue();
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (!(spec.raw("table") instanceof List<?> table) || table.isEmpty()) {
            errors.add(path + ".table: 항목이 하나 이상 있는 배열이어야 합니다");
            return errors;
        }
        for (int i = 0; i < table.size(); i++) {
            String rowPath = path + ".table[" + i + "]";
            if (!(table.get(i) instanceof Map<?, ?> row)) {
                errors.add(rowPath + ": 객체여야 합니다");
                continue;
            }
            for (Object key : row.keySet()) {
                if (!Set.of("weight", "effects").contains(String.valueOf(key))) {
                    errors.add(rowPath + "." + key + ": 알 수 없는 키입니다 (weight, effects)");
                }
            }
            if (!(row.get("weight") instanceof Integer w) || w < 1) {
                errors.add(rowPath + ".weight: 1 이상의 정수가 필요합니다");
            }
            errors.addAll(check.effects(row.get("effects"), rowPath + ".effects"));
        }
        return errors;
    }
}
