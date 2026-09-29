package com.cardbattle.engine.effect.custom;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.CustomEffect;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 블랙홀: 걸려 있는 저주를 모두 풀고, 그 저주 카드들을 게임에서 제외한다(더 이상 뽑히지 않음).
 * 제외된 저주가 하나라도 있으면 이 카드도 함께 제외된다.
 * <pre>{ "type": "CUSTOM", "handler": "BANISH_CURSES" }</pre>
 */
public final class BanishCurses implements CustomEffect.Handler {

    @Override
    public String name() {
        return "BANISH_CURSES";
    }

    @Override
    public Set<String> params() {
        return Set.of();
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        List<String> banned = new ArrayList<>();
        for (PlayerState p : ctx.state().alivePlayers()) {
            if (p.cursed()) {
                String cardId = p.getCurse().getCardId();
                ctx.removeCurse(p, "BANISHED");
                if (cardId != null && !banned.contains(cardId)) {
                    banned.add(cardId);
                }
            }
        }
        if (banned.isEmpty()) {
            return;
        }
        if (ctx.card() != null) {
            banned.add(ctx.card().id());
        }
        for (String id : banned) {
            if (!ctx.state().getBannedCardIds().contains(id)) {
                ctx.state().getBannedCardIds().add(id);
            }
        }
        ctx.events().toAll(EventType.CARDS_BANNED, payload("cardIds", List.copyOf(banned)));
    }
}
