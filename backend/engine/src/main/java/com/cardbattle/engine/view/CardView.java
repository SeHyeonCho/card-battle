package com.cardbattle.engine.view;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.Targeting;

import java.util.List;

/** 클라이언트가 카드를 그리는 데 필요한 정보 (효과 내부 구조는 보내지 않는다). */
public record CardView(String id, String name, CardCategory category, String subcategory, Integer attack,
                       Integer attackMin, Integer attackMax, List<String> tags, Targeting targeting,
                       String description, String flavor) {

    public static CardView of(CardDefinition d) {
        return new CardView(d.id(), d.name(), d.category(), d.subcategory(), d.attack(), d.attackMin(),
                d.attackMax(), d.tags().stream().sorted().toList(), d.targeting(), d.description(), d.flavor());
    }
}
