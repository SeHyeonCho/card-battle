package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 카드 필터 (PRD 8.3). 필드 락·저주 락·추가 제출 등에서 "어떤 카드인가"를 고를 때 쓴다.
 * 지정한 키는 모두 만족(AND)해야 한다.
 *
 * <pre>
 * { "category": "ATTACK", "attackGte": 30 }            공격력 30 이상 공격 카드
 * { "category": ["SUPPORT", "BENEFIT"] }                보조 또는 이득 카드
 * { "tags": ["FIRE", "ELECTRIC"] }                      화염 또는 전기 속성
 * { "hasEffects": true }                                반동(자기 피해)을 뺀 효과가 있는 카드
 * { "hasEffects": ["HEAL", "DRAIN"] }                   이 효과 중 하나라도 있는 카드
 * { "cardIds": [...] } / { "excludeCardIds": [...] }    특정 카드만 / 특정 카드 제외
 * </pre>
 *
 * 랜덤 공격력 카드는 최대 공격력으로 attackGte/attackLte를 판정한다.
 */
public final class CardFilter {

    public static final Set<String> KEYS = Set.of(
            "category", "subcategory", "tags", "attackGte", "attackLte", "hasEffects", "cardIds", "excludeCardIds");

    private CardFilter() {
    }

    public static boolean matches(Map<String, Object> filter, CardDefinition card) {
        if (filter == null || card == null) {
            return filter == null;
        }
        Object category = filter.get("category");
        if (category != null && !strings(category).contains(card.category().name())) {
            return false;
        }
        Object sub = filter.get("subcategory");
        if (sub != null && (card.subcategory() == null || !strings(sub).contains(card.subcategory()))) {
            return false;
        }
        Object tags = filter.get("tags");
        if (tags != null && strings(tags).stream().noneMatch(card::hasTag)) {
            return false;
        }
        Integer attack = maxAttack(card);
        if (filter.get("attackGte") instanceof Number n && (attack == null || attack < n.intValue())) {
            return false;
        }
        if (filter.get("attackLte") instanceof Number n && (attack == null || attack > n.intValue())) {
            return false;
        }
        Object hasEffects = filter.get("hasEffects");
        if (Boolean.TRUE.equals(hasEffects) && card.effects().stream().allMatch(CardFilter::selfRecoil)) {
            return false;
        }
        if (Boolean.FALSE.equals(hasEffects) && !card.effects().stream().allMatch(CardFilter::selfRecoil)) {
            return false;
        }
        if (hasEffects instanceof List<?> types) {
            List<String> wanted = strings(types);
            if (card.effects().stream().noneMatch(e -> wanted.contains(e.type()))) {
                return false;
            }
        }
        Object ids = filter.get("cardIds");
        if (ids != null && !strings(ids).contains(card.id())) {
            return false;
        }
        Object exclude = filter.get("excludeCardIds");
        return exclude == null || !strings(exclude).contains(card.id());
    }

    /** 원작 탈모 규칙: 반동(자기 체력 감소)은 효과로 치지 않는다 */
    private static boolean selfRecoil(EffectSpec e) {
        return "DAMAGE".equals(e.type()) && "SELF".equals(e.str("target"));
    }

    private static Integer maxAttack(CardDefinition card) {
        if (card.randomAttack()) {
            return card.attackMax();
        }
        return card.attack();
    }

    private static List<String> strings(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            list.forEach(x -> out.add(String.valueOf(x)));
        } else if (v != null) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    /** 팩 검증용 */
    public static List<String> validate(Object raw, CardPack pack, String path) {
        List<String> errors = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> filter)) {
            errors.add(path + ": 객체여야 합니다");
            return errors;
        }
        for (Object key : filter.keySet()) {
            if (!KEYS.contains(String.valueOf(key))) {
                errors.add(path + "." + key + ": 알 수 없는 필터 키입니다 (지원: " + KEYS + ")");
            }
        }
        Object category = filter.get("category");
        if (category != null) {
            for (String c : strings(category)) {
                try {
                    CardCategory.valueOf(c);
                } catch (IllegalArgumentException e) {
                    errors.add(path + ".category: 알 수 없는 분류 '" + c + "'");
                }
            }
        }
        for (String key : List.of("attackGte", "attackLte")) {
            if (filter.containsKey(key) && !(filter.get(key) instanceof Number)) {
                errors.add(path + "." + key + ": 숫자여야 합니다");
            }
        }
        Object hasEffects = filter.get("hasEffects");
        if (hasEffects != null && !(hasEffects instanceof Boolean) && !(hasEffects instanceof List<?>)) {
            errors.add(path + ".hasEffects: true/false 또는 효과 타입 배열이어야 합니다");
        }
        for (String key : List.of("cardIds", "excludeCardIds")) {
            for (String id : strings(filter.get(key))) {
                if (pack.card(id) == null) {
                    errors.add(path + "." + key + ": 팩에 없는 카드 '" + id + "'");
                }
            }
        }
        return errors;
    }
}
