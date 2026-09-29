package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TargetResolver;
import com.cardbattle.engine.rules.TurnContext;

import java.util.List;

/** 핸들러 검증에서 자주 쓰는 공통 검사. */
final class Validations {

    private Validations() {
    }

    static void requireTarget(EffectSpec spec, String key, String path, List<String> errors) {
        String target = spec.str(key);
        if (target == null) {
            errors.add(path + "." + key + ": 대상이 필요합니다");
        } else if (!TargetResolver.TARGETS.contains(target)) {
            errors.add(path + "." + key + ": 알 수 없는 대상 '" + target + "'");
        }
    }

    static void requirePositiveInt(EffectSpec spec, String key, String path, List<String> errors) {
        if (!spec.integerParam(key) || spec.integer(key) <= 0) {
            errors.add(path + "." + key + ": 1 이상의 정수가 필요합니다");
        }
    }

    static void requireProbability(EffectSpec spec, String key, String path, List<String> errors) {
        if (!(spec.raw(key) instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 1) {
            errors.add(path + "." + key + ": 0~1 사이의 확률이 필요합니다");
        }
    }

    /** [0, 1) 난수가 p보다 작으면 참. 시드 기반이라 재현된다 */
    static boolean roll(TurnContext ctx, double p) {
        return ctx.random(1_000_000) < Math.round(p * 1_000_000);
    }

    /** key가 있으면 팩에 있는 카드 ID 배열이어야 한다 */
    static void requireCardIds(EffectSpec spec, String key, String path, PackCheck check, List<String> errors) {
        Object raw = spec.raw(key);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> ids)) {
            errors.add(path + "." + key + ": 카드 ID 배열이어야 합니다");
            return;
        }
        for (Object id : ids) {
            if (check.pack().card(String.valueOf(id)) == null) {
                errors.add(path + "." + key + ": 팩에 없는 카드 '" + id + "'");
            }
        }
    }
}
