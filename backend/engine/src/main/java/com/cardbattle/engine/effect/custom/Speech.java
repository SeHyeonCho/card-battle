package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.effect.PackCheck;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 연설: 손패를 모두 버리고 정해진 강한 카드로 바꾼다. 손패 한도는 handLimit로 돌아간다.
 * <pre>{ "type": "CUSTOM", "handler": "SPEECH", "handLimit": 5,
 *   "picks": [ ["p.time_stop"], ["p.storm"], ["p.one_punch"], ["p.snipe", "p.no_way", "p.das_boots"], ["RANDOM"] ] }</pre>
 * 칸마다 앞에서부터 다른 사람이 가지고 있지 않은 카드를 고르고, 전부 누가 가지고 있으면 무작위 카드를 받는다.
 * "RANDOM"은 무작위 카드.
 */
public final class Speech implements CustomEffect.Handler {

    private static final String RANDOM = "RANDOM";

    @Override
    public String name() {
        return "SPEECH";
    }

    @Override
    public Set<String> params() {
        return Set.of("picks", "handLimit");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        PlayerState actor = ctx.actor();
        ctx.setHandLimit(actor, spec.integer("handLimit", ctx.state().getSettings().handSize()));
        actor.getHand().clear();
        for (Object slot : (List<?>) spec.raw("picks")) {
            actor.getHand().add(pick(ctx, (List<?>) slot));
        }
        ctx.handChanged(actor);
    }

    private static CardInstance pick(TurnContext ctx, List<?> candidates) {
        for (Object c : candidates) {
            String cardId = String.valueOf(c);
            if (!RANDOM.equals(cardId) && !heldByOthers(ctx, cardId)) {
                return ctx.newCard(cardId);
            }
        }
        return ctx.drawRandom();
    }

    private static boolean heldByOthers(TurnContext ctx, String cardId) {
        for (PlayerState p : ctx.state().alivePlayers()) {
            if (p != ctx.actor() && p.getHand().stream().anyMatch(i -> i.cardId().equals(cardId))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (!(spec.raw("picks") instanceof List<?> picks) || picks.isEmpty()) {
            errors.add(path + ".picks: 카드 후보 배열의 배열이 필요합니다");
            return errors;
        }
        for (int i = 0; i < picks.size(); i++) {
            if (!(picks.get(i) instanceof List<?> slot) || slot.isEmpty()) {
                errors.add(path + ".picks[" + i + "]: 카드 ID 배열이어야 합니다");
                continue;
            }
            for (Object id : slot) {
                if (!RANDOM.equals(id) && check.pack().card(String.valueOf(id)) == null) {
                    errors.add(path + ".picks[" + i + "]: 팩에 없는 카드 '" + id + "'");
                }
            }
        }
        if (spec.raw("handLimit") != null && !(spec.integer("handLimit") != null && spec.integer("handLimit") > 0)) {
            errors.add(path + ".handLimit: 1 이상의 정수가 필요합니다");
        }
        return errors;
    }
}
