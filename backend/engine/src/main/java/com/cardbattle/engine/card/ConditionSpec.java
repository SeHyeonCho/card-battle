package com.cardbattle.engine.card;

import java.util.List;
import java.util.Map;

/**
 * 제출 조건 하나 (PRD 8.2).
 *
 * <p>{@code ANY}는 {@code of} 배열을, {@code NOT}은 {@code condition} 객체를
 * {@code children}으로 가진다.
 */
public record ConditionSpec(String type, Map<String, Object> params, List<ConditionSpec> children) {

    public ConditionSpec {
        params = Params.copy(params);
        children = children == null ? List.of() : List.copyOf(children);
    }

    public String str(String key) {
        return Params.str(params, key);
    }

    public Integer integer(String key) {
        return Params.integer(params, key);
    }

    public boolean integerParam(String key) {
        return Params.isInteger(params, key);
    }
}
