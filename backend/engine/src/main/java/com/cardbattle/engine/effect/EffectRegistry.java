package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.rules.TurnContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** type 문자열 → 핸들러. 등록되지 않은 type을 쓴 카드팩은 import 단계에서 거부된다. */
public final class EffectRegistry {

    private final Map<String, EffectHandler> handlers = new LinkedHashMap<>();

    public EffectRegistry(List<EffectHandler> handlers) {
        for (EffectHandler h : handlers) {
            if (this.handlers.putIfAbsent(h.type(), h) != null) {
                throw new IllegalArgumentException("duplicate effect handler: " + h.type());
            }
            if (h instanceof NestedEffects nested) {
                nested.bind(this);
            }
        }
    }

    /** 기본 프리미티브 세트 (PRD 8.3). 핸들러는 상태를 갖지 않으므로 매번 새로 만들어도 된다 */
    public static EffectRegistry defaults() {
        return new EffectRegistry(List.of(
                // 누적
                new ModifyAccumulatedEffect(),
                new ResetAccumulatedEffect(),
                new TransferAccumulatedEffect(),
                new SwapAttackAndAccumulatedEffect(),
                new AbsorbAccumulatedEffect(),
                new ImmuneThisTurnEffect(),
                // 체력
                new DamageEffect(),
                new HealEffect(),
                new DrainEffect(),
                new SetHpEffect(),
                new SwapHpEffect(),
                new EqualizeHpEffect(),
                new HalveHpEffect(),
                // 공격 보정·손패
                new ConditionalAttackEffect(),
                new HandLimitEffect(),
                // 저주·지속 상태
                new ApplyCurseEffect(),
                new RemoveCurseEffect(),
                new ReflectCurseEffect(),
                new ApplyStatusEffect(),
                new DispelStatusesEffect(),
                // 확률
                new ChanceEffect(),
                new RandomChoiceEffect()));
    }

    public EffectHandler handler(String type) {
        return handlers.get(type);
    }

    public Set<String> supportedTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /** 모든 효과가 공통으로 받는 파라미터: 조건부 발동 */
    public static final String WHEN = "when";

    public void run(TurnContext ctx, List<EffectSpec> effects) {
        for (EffectSpec spec : effects) {
            EffectHandler h = handlers.get(spec.type());
            if (h == null) {
                throw new IllegalStateException("unsupported effect type: " + spec.type());
            }
            Object when = spec.raw(WHEN);
            if (when != null && !ctx.test(PackParser.condition(when, spec.type() + "." + WHEN))) {
                continue;
            }
            h.apply(ctx, spec);
        }
    }
}
