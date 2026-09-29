package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.pack.PackFormatException;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.rules.CardFilter;
import com.cardbattle.engine.rules.ConditionEvaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * 카드팩 검증 중에 효과 핸들러가 쓰는 도구 모음.
 * 확률·무작위 선택·저주처럼 효과 안에 효과가 들어 있는 경우 재귀적으로 검증한다.
 */
public record PackCheck(CardPack pack, ConditionEvaluator conditions, EffectRegistry effects) {

    public PackCheck(CardPack pack, EffectRegistry effects) {
        this(pack, new ConditionEvaluator(pack), effects);
    }

    /** 효과 하나를 전부 검증한다: 지원 여부, 파라미터 이름, when, 핸들러별 검사 */
    public List<String> effect(EffectSpec e, String path) {
        List<String> errors = new ArrayList<>();
        EffectHandler handler = effects.handler(e.type());
        if (handler == null) {
            errors.add(path + ".type: 지원하지 않는 효과 '" + e.type() + "' (지원: " + effects.supportedTypes() + ")");
            return errors;
        }
        errors.addAll(handler.validate(e, path, this));
        for (String key : e.params().keySet()) {
            if (!EffectRegistry.WHEN.equals(key) && !handler.params().contains(key)) {
                errors.add(path + "." + key + ": " + e.type() + " 에서 쓸 수 없는 파라미터입니다 (지원: "
                        + handler.params() + ", when)");
            }
        }
        if (e.raw(EffectRegistry.WHEN) != null) {
            errors.addAll(condition(e.raw(EffectRegistry.WHEN), path + ".when"));
        }
        return errors;
    }

    /** 효과 배열(JSON 그대로)을 읽어 하나씩 검증한다 */
    public List<String> effects(Object raw, String path) {
        List<String> errors = new ArrayList<>();
        try {
            List<EffectSpec> list = PackParser.effects(raw, path);
            for (int i = 0; i < list.size(); i++) {
                errors.addAll(effect(list.get(i), path + "[" + i + "]"));
            }
        } catch (PackFormatException ex) {
            errors.addAll(ex.errors());
        }
        return errors;
    }

    public List<String> condition(Object raw, String path) {
        try {
            ConditionSpec c = PackParser.condition(raw, path);
            return conditions.validate(c, path);
        } catch (PackFormatException ex) {
            return ex.errors();
        }
    }

    public List<String> filter(Object raw, String path) {
        return CardFilter.validate(raw, pack, path);
    }
}
