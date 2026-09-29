package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TargetResolver;

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
}
