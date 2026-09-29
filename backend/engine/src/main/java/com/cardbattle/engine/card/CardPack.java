package com.cardbattle.engine.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카드팩. 엔진은 카드 내용을 모르고 이 객체만 본다.
 * 팩을 바꾸면 엔진 코드를 고치지 않고도 다른 카드로 게임할 수 있다.
 */
public final class CardPack {

    private final String code;
    private final String name;
    private final int version;
    private final String visibility;
    private final String ruleMode;
    private final List<CardDefinition> cards;
    private final Map<String, CardDefinition> byId = new LinkedHashMap<>();
    private final List<CardDefinition> drawable = new ArrayList<>();
    private final int[] cumulativeWeights;
    private final int totalWeight;

    public CardPack(String code, String name, int version, String visibility, String ruleMode,
                    List<CardDefinition> cards) {
        this.code = code;
        this.name = name;
        this.version = version;
        this.visibility = visibility;
        this.ruleMode = ruleMode;
        this.cards = List.copyOf(cards);
        for (CardDefinition card : this.cards) {
            byId.putIfAbsent(card.id(), card); // 중복 ID는 PackValidator가 잡는다
            if (card.weight() > 0) {
                drawable.add(card);
            }
        }
        cumulativeWeights = new int[drawable.size()];
        int sum = 0;
        for (int i = 0; i < drawable.size(); i++) {
            sum += drawable.get(i).weight();
            cumulativeWeights[i] = sum;
        }
        totalWeight = sum;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public String visibility() {
        return visibility;
    }

    public String ruleMode() {
        return ruleMode;
    }

    public List<CardDefinition> cards() {
        return cards;
    }

    public CardDefinition card(String id) {
        return byId.get(id);
    }

    public int totalWeight() {
        return totalWeight;
    }

    /** roll은 [0, totalWeight) 범위. 가중치 비율대로 카드를 고른다 (PRD 7.9). */
    public CardDefinition cardForRoll(int roll) {
        if (roll < 0 || roll >= totalWeight) {
            throw new IllegalArgumentException("roll out of range: " + roll);
        }
        int lo = 0;
        int hi = cumulativeWeights.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (roll < cumulativeWeights[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return drawable.get(lo);
    }
}
