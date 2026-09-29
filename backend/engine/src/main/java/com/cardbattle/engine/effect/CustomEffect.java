package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.effect.custom.BanishCurses;
import com.cardbattle.engine.effect.custom.BaseballBat;
import com.cardbattle.engine.effect.custom.ClearField;
import com.cardbattle.engine.effect.custom.RotateHp;
import com.cardbattle.engine.effect.custom.ShareAccumulated;
import com.cardbattle.engine.effect.custom.Speech;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 프리미티브로 표현하기 어려운 카드 한두 장 전용 동작 (PRD 8.3 CUSTOM).
 * 카드별 코드가 엔진 전체에 퍼지지 않도록 여기 모아 두고, 같은 패턴이 반복되면 프리미티브로 올린다.
 * <pre>{ "type": "CUSTOM", "handler": "SPEECH", ... 핸들러별 파라미터 }</pre>
 */
public final class CustomEffect implements EffectHandler {

    /** 커스텀 동작 하나 */
    public interface Handler {

        String name();

        Set<String> params();

        void apply(TurnContext ctx, EffectSpec spec);

        default List<String> validate(EffectSpec spec, String path, PackCheck check) {
            return List.of();
        }
    }

    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    public CustomEffect() {
        for (Handler h : List.of(new Speech(), new BaseballBat(), new ClearField(), new RotateHp(),
                new ShareAccumulated(), new BanishCurses())) {
            handlers.put(h.name(), h);
        }
    }

    @Override
    public String type() {
        return "CUSTOM";
    }

    @Override
    public Set<String> params() {
        Set<String> all = new HashSet<>();
        all.add("handler");
        handlers.values().forEach(h -> all.addAll(h.params()));
        return all;
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        handlers.get(spec.str("handler")).apply(ctx, spec);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Handler h = handlers.get(spec.str("handler"));
        if (h == null) {
            errors.add(path + ".handler: 알 수 없는 커스텀 동작 '" + spec.str("handler") + "' (지원: "
                    + handlers.keySet() + ")");
            return errors;
        }
        for (String key : spec.params().keySet()) {
            if (!"handler".equals(key) && !EffectRegistry.WHEN.equals(key) && !h.params().contains(key)) {
                errors.add(path + "." + key + ": " + h.name() + " 에서 쓸 수 없는 파라미터입니다 (지원: " + h.params() + ")");
            }
        }
        errors.addAll(h.validate(spec, path, check));
        return errors;
    }
}
