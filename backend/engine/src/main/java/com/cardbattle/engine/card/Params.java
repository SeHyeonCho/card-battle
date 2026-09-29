package com.cardbattle.engine.card;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 효과·조건 파라미터(Map)를 안전하게 읽는 도우미. */
final class Params {

    private Params() {
    }

    static Map<String, Object> copy(Map<String, Object> params) {
        return params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    static Integer integer(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : null;
    }

    static boolean isInteger(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte;
    }

    static boolean bool(Map<String, Object> params, String key, boolean defaultValue) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : defaultValue;
    }
}
