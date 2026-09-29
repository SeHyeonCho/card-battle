package com.cardbattle.engine.card;

import java.util.List;
import java.util.Set;

/**
 * 카드 한 종류의 정의 (PRD 8.1). 카드팩 JSON 한 항목에 해당한다.
 *
 * <p>공격력은 {@code attack}(고정) 또는 {@code attackMin~attackMax}(랜덤) 중 하나를 쓴다.
 * 랜덤 공격력은 카드를 낼 때 굴린다.
 */
public record CardDefinition(
        String id,
        String name,
        CardCategory category,
        String subcategory,
        Integer attack,
        Integer attackMin,
        Integer attackMax,
        Set<String> tags,
        Targeting targeting,
        boolean alwaysPlayable,
        List<ConditionSpec> conditions,
        List<EffectSpec> effects,
        String description,
        String flavor,
        int weight) {

    public CardDefinition {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        targeting = targeting == null ? Targeting.NONE : targeting;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** 공격 카드인가. (Jackson이 속성으로 오인하지 않도록 is/get 접두사를 쓰지 않는다) */
    public boolean attackCard() {
        return category == CardCategory.ATTACK;
    }

    public boolean randomAttack() {
        return attackMin != null && attackMax != null;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public List<EffectSpec> effectsAt(Timing timing) {
        return effects.stream().filter(e -> e.timing() == timing).toList();
    }
}
