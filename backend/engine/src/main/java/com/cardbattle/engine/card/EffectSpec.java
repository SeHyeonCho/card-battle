package com.cardbattle.engine.card;

import java.util.Map;

/**
 * 카드 효과 하나 (PRD 8.3).
 *
 * <p>JSON의 {@code { "type": "DAMAGE", "target": "SELF", "amount": 10 }} 에서
 * type과 timing을 뺀 나머지 키가 {@code params}가 된다.
 */
public record EffectSpec(String type, Timing timing, Map<String, Object> params) {

    public EffectSpec {
        if (timing == null) {
            timing = Timing.ON_PLAY;
        }
        params = Params.copy(params);
    }

    public String str(String key) {
        return Params.str(params, key);
    }

    public String str(String key, String defaultValue) {
        String v = Params.str(params, key);
        return v == null ? defaultValue : v;
    }

    public Integer integer(String key) {
        return Params.integer(params, key);
    }

    public int integer(String key, int defaultValue) {
        Integer v = Params.integer(params, key);
        return v == null ? defaultValue : v;
    }

    public boolean integerParam(String key) {
        return Params.isInteger(params, key);
    }

    /** 가공하지 않은 파라미터 값 (중첩 객체·배열용) */
    public Object raw(String key) {
        return params.get(key);
    }

    public boolean bool(String key, boolean defaultValue) {
        return Params.bool(params, key, defaultValue);
    }
}
