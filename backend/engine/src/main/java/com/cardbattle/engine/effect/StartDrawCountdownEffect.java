package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * (base + perPlayer × 생존자 수) 턴 뒤에 게임을 무승부로 끝낸다 (카페베네). 이미 있으면 새로 시작한다.
 * <pre>{ "type": "START_DRAW_COUNTDOWN", "base": 50, "perPlayer": 20 }</pre>
 */
public final class StartDrawCountdownEffect implements EffectHandler {

    @Override
    public String type() {
        return "START_DRAW_COUNTDOWN";
    }

    @Override
    public Set<String> params() {
        return Set.of("base", "perPlayer");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        int turns = spec.integer("base", 0) + spec.integer("perPlayer", 0) * ctx.state().alivePlayers().size();
        ctx.state().setDrawCountdown(turns);
        ctx.events().toAll(EventType.DRAW_COUNTDOWN_CHANGED, payload("turnsLeft", turns));
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requirePositiveInt(spec, "base", path, errors);
        if (spec.raw("perPlayer") != null && (!spec.integerParam("perPlayer") || spec.integer("perPlayer") < 0)) {
            errors.add(path + ".perPlayer: 0 이상의 정수가 필요합니다");
        }
        return errors;
    }
}
