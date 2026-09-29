package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 카드 제출 조건을 평가한다 (PRD 8.2). 조건 목록은 모두 만족(AND)해야 한다.
 * 효과의 {@code when} 파라미터(조건부 발동)도 같은 평가기를 쓴다.
 */
public final class ConditionEvaluator {

    public static final Set<String> SUPPORTED = Set.of(
            "SELF_HP_LTE", "SELF_HP_GTE", "ACCUMULATED_GTE", "ACCUMULATED_ZERO",
            "FIELD_HAS_TAG", "FIELD_HAS_CARD", "SELF_CURSED", "HAND_HAS", "ANY", "NOT");

    private final CardPack pack;

    public ConditionEvaluator(CardPack pack) {
        this.pack = pack;
    }

    public boolean testAll(List<ConditionSpec> conditions, GameState state, PlayerState self) {
        for (ConditionSpec c : conditions) {
            if (!test(c, state, self)) {
                return false;
            }
        }
        return true;
    }

    public boolean test(ConditionSpec c, GameState state, PlayerState self) {
        return switch (c.type()) {
            case "SELF_HP_LTE" -> self.getHp() <= c.integer("value");
            case "SELF_HP_GTE" -> self.getHp() >= c.integer("value");
            case "ACCUMULATED_GTE" -> state.getAccumulatedDamage() >= c.integer("value");
            case "ACCUMULATED_ZERO" -> state.getAccumulatedDamage() == 0;
            case "FIELD_HAS_TAG" -> fieldHasTag(state, c.str("tag"));
            case "FIELD_HAS_CARD" -> fieldHasCard(state, c.str("cardId"));
            case "SELF_CURSED" -> self.cursed();
            case "HAND_HAS" -> handCount(self, c.params().get("filter")) >= c.integer("count");
            case "ANY" -> c.children().stream().anyMatch(child -> test(child, state, self));
            case "NOT" -> !test(c.children().get(0), state, self);
            default -> throw new IllegalArgumentException("unknown condition: " + c.type());
        };
    }

    private boolean fieldHasTag(GameState state, String tag) {
        for (FieldCard f : state.getField()) {
            CardDefinition def = pack.card(f.cardId());
            if (def != null && def.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private int handCount(PlayerState self, Object filter) {
        int n = 0;
        for (CardInstance inst : self.getHand()) {
            if (CardFilter.matches((Map<String, Object>) filter, pack.card(inst.cardId()))) {
                n++;
            }
        }
        return n;
    }

    private boolean fieldHasCard(GameState state, String cardId) {
        return state.getField().stream().anyMatch(f -> f.cardId().equals(cardId));
    }

    /** 팩 검증용. 문제가 있으면 오류 메시지 목록을 돌려준다 */
    public List<String> validate(ConditionSpec c, String path) {
        List<String> errors = new ArrayList<>();
        if (c.type() == null || !SUPPORTED.contains(c.type())) {
            errors.add(path + ".type: 지원하지 않는 조건 '" + c.type() + "'");
            return errors;
        }
        switch (c.type()) {
            case "SELF_HP_LTE", "SELF_HP_GTE", "ACCUMULATED_GTE" -> {
                if (!c.integerParam("value")) {
                    errors.add(path + ".value: 정수가 필요합니다");
                }
            }
            case "FIELD_HAS_TAG" -> {
                if (c.str("tag") == null || c.str("tag").isBlank()) {
                    errors.add(path + ".tag: 값이 필요합니다");
                }
            }
            case "FIELD_HAS_CARD" -> {
                String cardId = c.str("cardId");
                if (cardId == null || pack.card(cardId) == null) {
                    errors.add(path + ".cardId: 팩에 없는 카드 '" + cardId + "'");
                }
            }
            case "HAND_HAS" -> {
                errors.addAll(CardFilter.validate(c.params().get("filter"), pack, path + ".filter"));
                if (!c.integerParam("count") || c.integer("count") < 1) {
                    errors.add(path + ".count: 1 이상의 정수가 필요합니다");
                }
            }
            case "ANY" -> {
                if (c.children().isEmpty()) {
                    errors.add(path + ".of: 하위 조건이 하나 이상 필요합니다");
                }
                for (int i = 0; i < c.children().size(); i++) {
                    errors.addAll(validate(c.children().get(i), path + ".of[" + i + "]"));
                }
            }
            case "NOT" -> {
                if (c.children().size() != 1) {
                    errors.add(path + ".condition: 하위 조건이 정확히 하나 필요합니다");
                } else {
                    errors.addAll(validate(c.children().get(0), path + ".condition"));
                }
            }
            default -> {
                // ACCUMULATED_ZERO 등 파라미터 없음
            }
        }
        return errors;
    }
}
