package com.cardbattle.engine.pack;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.card.Timing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON을 Map으로 읽은 결과를 CardPack으로 바꾼다.
 *
 * <p>엔진이 JSON 라이브러리에 의존하지 않도록, JSON 파싱(문자열 → Map)은 서버가 하고
 * 여기서는 Map → 카드 정의 변환과 형식 검사만 한다. 의미 검사는 {@link PackValidator}가 한다.
 */
public final class PackParser {

    private static final Set<String> PACK_KEYS = Set.of(
            "code", "name", "version", "visibility", "ruleMode", "description");
    private static final Set<String> CARD_KEYS = Set.of(
            "id", "name", "category", "subcategory", "attack", "attackRange", "tags", "targeting",
            "alwaysPlayable", "conditions", "effects", "description", "flavor", "weight");

    private PackParser() {
    }

    /**
     * @param meta  pack.json 내용
     * @param cards cards/*.json 에 들어 있는 카드 객체들
     * @throws PackFormatException 형식 오류가 하나라도 있으면 전부 모아서 던진다
     */
    public static CardPack parse(Map<String, Object> meta, List<Map<String, Object>> cards) {
        List<String> errors = new ArrayList<>();
        unknownKeys(meta, PACK_KEYS, "pack", errors);
        String code = string(meta, "code", "pack", true, errors);
        String name = string(meta, "name", "pack", true, errors);
        Integer version = integer(meta, "version", "pack", true, errors);
        String visibility = string(meta, "visibility", "pack", false, errors);
        String ruleMode = string(meta, "ruleMode", "pack", false, errors);

        List<CardDefinition> defs = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Map<String, Object> m = cards.get(i);
            Object id = m.get("id");
            String path = "cards[" + i + "]" + (id == null ? "" : "(" + id + ")");
            CardDefinition def = parseCard(m, path, errors);
            if (def != null) {
                defs.add(def);
            }
        }
        if (!errors.isEmpty()) {
            throw new PackFormatException(errors);
        }
        return new CardPack(code, name, version,
                visibility == null ? "PUBLIC" : visibility,
                ruleMode == null ? "SC1" : ruleMode,
                defs);
    }

    /** 카드 하나 변환 (DB에서 읽을 때도 쓴다) */
    public static CardDefinition parseCard(Map<String, Object> m) {
        List<String> errors = new ArrayList<>();
        CardDefinition def = parseCard(m, "card(" + m.get("id") + ")", errors);
        if (!errors.isEmpty()) {
            throw new PackFormatException(errors);
        }
        return def;
    }

    private static CardDefinition parseCard(Map<String, Object> m, String path, List<String> errors) {
        int before = errors.size();
        unknownKeys(m, CARD_KEYS, path, errors);
        String id = string(m, "id", path, true, errors);
        String name = string(m, "name", path, true, errors);
        CardCategory category = enumValue(m, "category", CardCategory.class, path, true, errors);
        String subcategory = string(m, "subcategory", path, false, errors);
        Integer attack = integer(m, "attack", path, false, errors);
        Integer attackMin = null;
        Integer attackMax = null;
        if (m.containsKey("attackRange")) {
            Object r = m.get("attackRange");
            if (r instanceof List<?> list && list.size() == 2 && isInt(list.get(0)) && isInt(list.get(1))) {
                attackMin = ((Number) list.get(0)).intValue();
                attackMax = ((Number) list.get(1)).intValue();
            } else {
                errors.add(path + ".attackRange: [최소, 최대] 형태의 정수 두 개여야 합니다");
            }
        }
        Set<String> tags = new LinkedHashSet<>();
        if (m.containsKey("tags")) {
            if (m.get("tags") instanceof List<?> list && list.stream().allMatch(t -> t instanceof String)) {
                list.forEach(t -> tags.add((String) t));
            } else {
                errors.add(path + ".tags: 문자열 배열이어야 합니다");
            }
        }
        Targeting targeting = enumValue(m, "targeting", Targeting.class, path, false, errors);
        boolean alwaysPlayable = false;
        if (m.containsKey("alwaysPlayable")) {
            if (m.get("alwaysPlayable") instanceof Boolean b) {
                alwaysPlayable = b;
            } else {
                errors.add(path + ".alwaysPlayable: true/false 여야 합니다");
            }
        }
        List<ConditionSpec> conditions = new ArrayList<>();
        for (Object o : list(m, "conditions", path, errors)) {
            ConditionSpec c = parseCondition(o, path + ".conditions[" + conditions.size() + "]", errors);
            conditions.add(c);
        }
        List<EffectSpec> effects = new ArrayList<>();
        List<Object> rawEffects = list(m, "effects", path, errors);
        for (int i = 0; i < rawEffects.size(); i++) {
            EffectSpec e = parseEffect(rawEffects.get(i), path + ".effects[" + i + "]", errors);
            if (e != null) {
                effects.add(e);
            }
        }
        String description = string(m, "description", path, true, errors);
        String flavor = string(m, "flavor", path, false, errors);
        Integer weight = integer(m, "weight", path, true, errors);

        if (errors.size() > before) {
            return null;
        }
        conditions.removeIf(c -> c == null);
        return new CardDefinition(id, name, category, subcategory, attack, attackMin, attackMax, tags,
                targeting, alwaysPlayable, conditions, effects, description, flavor, weight);
    }

    /**
     * 효과 파라미터 안에 든 조건 객체(예: {@code when})를 ConditionSpec으로 바꾼다.
     * 형식이 틀리면 {@link PackFormatException}을 던진다. 의미 검사는 ConditionEvaluator.validate가 한다.
     */
    public static ConditionSpec condition(Object raw, String path) {
        List<String> errors = new ArrayList<>();
        ConditionSpec c = parseCondition(raw, path, errors);
        if (c == null && errors.isEmpty()) {
            errors.add(path + ": 조건 형식이 잘못됐습니다");
        }
        if (!errors.isEmpty()) {
            throw new PackFormatException(errors);
        }
        return c;
    }

    /** 효과 파라미터 안에 든 효과 배열(예: CHANCE의 then)을 EffectSpec 목록으로 바꾼다 */
    public static List<EffectSpec> effects(Object raw, String path) {
        List<String> errors = new ArrayList<>();
        List<EffectSpec> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            errors.add(path + ": 효과 배열이어야 합니다");
        } else {
            for (int i = 0; i < list.size(); i++) {
                EffectSpec e = parseEffect(list.get(i), path + "[" + i + "]", errors);
                if (e != null) {
                    out.add(e);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new PackFormatException(errors);
        }
        return out;
    }

    private static EffectSpec parseEffect(Object o, String path, List<String> errors) {
        if (!(o instanceof Map<?, ?> raw)) {
            errors.add(path + ": 객체여야 합니다");
            return null;
        }
        Map<String, Object> m = stringKeys(raw);
        String type = string(m, "type", path, true, errors);
        Timing timing = enumValue(m, "timing", Timing.class, path, false, errors);
        Map<String, Object> params = new LinkedHashMap<>(m);
        params.remove("type");
        params.remove("timing");
        return type == null ? null : new EffectSpec(type, timing, params);
    }

    private static ConditionSpec parseCondition(Object o, String path, List<String> errors) {
        if (!(o instanceof Map<?, ?> raw)) {
            errors.add(path + ": 객체여야 합니다");
            return null;
        }
        Map<String, Object> m = stringKeys(raw);
        String type = string(m, "type", path, true, errors);
        List<ConditionSpec> children = new ArrayList<>();
        if (m.containsKey("of")) {
            List<Object> of = list(m, "of", path, errors);
            for (int i = 0; i < of.size(); i++) {
                ConditionSpec child = parseCondition(of.get(i), path + ".of[" + i + "]", errors);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        if (m.containsKey("condition")) {
            ConditionSpec child = parseCondition(m.get("condition"), path + ".condition", errors);
            if (child != null) {
                children.add(child);
            }
        }
        Map<String, Object> params = new LinkedHashMap<>(m);
        params.remove("type");
        params.remove("of");
        params.remove("condition");
        return type == null ? null : new ConditionSpec(type, params, children);
    }

    // ------------------------------------------------------------------
    // 형식 검사 도우미
    // ------------------------------------------------------------------

    private static void unknownKeys(Map<String, Object> m, Set<String> allowed, String path, List<String> errors) {
        for (String key : m.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(path + "." + key + ": 알 수 없는 필드입니다 (오타인지 확인하세요)");
            }
        }
    }

    private static String string(Map<String, Object> m, String key, String path, boolean required,
                                 List<String> errors) {
        Object v = m.get(key);
        if (v == null) {
            if (required) {
                errors.add(path + "." + key + ": 필수 값입니다");
            }
            return null;
        }
        if (!(v instanceof String s)) {
            errors.add(path + "." + key + ": 문자열이어야 합니다");
            return null;
        }
        return s;
    }

    private static Integer integer(Map<String, Object> m, String key, String path, boolean required,
                                   List<String> errors) {
        Object v = m.get(key);
        if (v == null) {
            if (required) {
                errors.add(path + "." + key + ": 필수 값입니다");
            }
            return null;
        }
        if (!isInt(v)) {
            errors.add(path + "." + key + ": 정수여야 합니다");
            return null;
        }
        return ((Number) v).intValue();
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> m, String key, Class<E> type, String path,
                                                   boolean required, List<String> errors) {
        String s = string(m, key, path, required, errors);
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            errors.add(path + "." + key + ": 알 수 없는 값 '" + s + "'");
            return null;
        }
    }

    private static List<Object> list(Map<String, Object> m, String key, String path, List<String> errors) {
        Object v = m.get(key);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> l)) {
            errors.add(path + "." + key + ": 배열이어야 합니다");
            return List.of();
        }
        return new ArrayList<>(l);
    }

    private static boolean isInt(Object v) {
        return v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte;
    }

    private static Map<String, Object> stringKeys(Map<?, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        raw.forEach((k, v) -> m.put(String.valueOf(k), v));
        return m;
    }
}
