package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 직전 필드의 카드 한 장을 내 손패로 가져온다 (조교). 가져온 카드가 건 필드 락도 함께 풀린다.
 * <pre>{ "type": "RETRIEVE_FROM_FIELD", "position": "LEFTMOST" }</pre>
 */
public final class RetrieveFromFieldEffect implements EffectHandler {

    @Override
    public String type() {
        return "RETRIEVE_FROM_FIELD";
    }

    @Override
    public Set<String> params() {
        return Set.of("position");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        GameState state = ctx.state();
        if (state.getField().isEmpty()) {
            return;
        }
        List<FieldCard> field = new ArrayList<>(state.getField());
        FieldCard taken = field.remove("RIGHTMOST".equals(spec.str("position")) ? field.size() - 1 : 0);
        state.setField(field);
        ctx.events().toAll(EventType.FIELD_CHANGED, payload("field", List.copyOf(field)));
        if (state.getFieldLocks().removeIf(l -> l.getSourceInstanceId().equals(taken.instanceId()))) {
            ctx.events().toAll(EventType.FIELD_LOCKS_CHANGED, payload("locks", List.copyOf(state.getFieldLocks())));
        }
        ctx.actor().getHand().add(ctx.newCard(taken.cardId()));
        ctx.handChanged(ctx.actor());
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (spec.raw("position") != null && !Set.of("LEFTMOST", "RIGHTMOST").contains(spec.str("position"))) {
            errors.add(path + ".position: LEFTMOST 또는 RIGHTMOST 여야 합니다");
        }
        return errors;
    }
}
