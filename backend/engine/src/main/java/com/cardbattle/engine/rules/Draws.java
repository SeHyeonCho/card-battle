package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;

import java.util.List;

/** 드로우 추첨 (PRD 7.9). 게임에서 제외된 카드(블랙홀)는 뽑히지 않는다. */
public final class Draws {

    private Draws() {
    }

    public static CardDefinition one(GameState state, CardPack pack) {
        List<String> banned = state.getBannedCardIds();
        if (banned.isEmpty()) {
            return pack.cardForRoll(GameRng.nextInt(state, pack.totalWeight()));
        }
        int total = 0;
        for (CardDefinition c : pack.cards()) {
            if (c.weight() > 0 && !banned.contains(c.id())) {
                total += c.weight();
            }
        }
        if (total == 0) {
            return pack.cardForRoll(GameRng.nextInt(state, pack.totalWeight())); // 전부 제외됐으면 규칙을 무시하고 뽑는다
        }
        int roll = GameRng.nextInt(state, total);
        for (CardDefinition c : pack.cards()) {
            if (c.weight() > 0 && !banned.contains(c.id())) {
                roll -= c.weight();
                if (roll < 0) {
                    return c;
                }
            }
        }
        throw new IllegalStateException("draw roll out of range");
    }
}
