package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.FieldLock;
import com.cardbattle.engine.state.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 이 카드가 필드에 있는 동안 필터에 맞는 카드를 낼 수 없게 한다 (물총, 욕설, 소방차, 광대, 기적, 벙커링 …).
 * <pre>{ "type": "FIELD_LOCK", "filter": { "category": "SUPPORT" }, "turns": 2 }</pre>
 * 다음 턴부터 turns 턴 동안 유지된다 (최대 2턴, PRD 7.7). 다른 카드를 내서 필드가 바뀌면 즉시 풀리고,
 * 버리기는 필드를 바꾸지 않으므로 락이 유지된다.
 */
public final class FieldLockEffect implements EffectHandler {

    public static final int MAX_TURNS = 2;

    @Override
    public String type() {
        return "FIELD_LOCK";
    }

    @Override
    public Set<String> params() {
        return Set.of("filter", "turns");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(TurnContext ctx, EffectSpec spec) {
        if (ctx.cardInstanceId() == null) {
            return; // 필드에 놓이는 카드만 락을 건다
        }
        GameState state = ctx.state();
        state.getFieldLocks().add(new FieldLock(ctx.cardInstanceId(), ctx.card().id(), ctx.actor().getPlayerId(),
                (Map<String, Object>) spec.raw("filter"), state.getTurnNumber() + spec.integer("turns", MAX_TURNS)));
        ctx.events().toAll(EventType.FIELD_LOCKS_CHANGED, payload("locks", List.copyOf(state.getFieldLocks())));
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        errors.addAll(check.filter(spec.raw("filter"), path + ".filter"));
        Integer turns = spec.integer("turns");
        if (!spec.integerParam("turns") || turns < 1 || turns > MAX_TURNS) {
            errors.add(path + ".turns: 1~" + MAX_TURNS + " 사이의 정수여야 합니다 (PRD 7.7 필드 락 최대 2턴)");
        }
        return errors;
    }
}
