package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 같은 턴에 카드를 한 장 더 낸다 (PRD 7.2 EXTRA_PLAY). 누적 판정은 추가로 낸 카드까지 모아 한 번에 한다.
 * <pre>
 * { "type": "EXTRA_PLAY", "filter": { "category": "ATTACK" }, "count": 1, "attackMode": "SUM",
 *   "discardIfNone": true }                                              폭풍: 공격 카드 한 장 더, 공격력 합산
 * { "type": "EXTRA_PLAY", "count": 1, "attackMode": "DOUBLE", "noDoubleCardIds": [...] }   슈퍼파워
 * { "type": "EXTRA_PLAY", "count": 1 }                                   시간아 멈춰라!: 아무 카드나 한 장 더
 * { "type": "EXTRA_PLAY", "filter": { "category": "ATTACK" }, "count": 1, "attackMode": "HEAL_SELF",
 *   "discardIfNone": true }                                              컨슘: 낸 공격력만큼 회복, 누적은 받는다
 * </pre>
 * attackMode를 생략하면 다음 카드를 그대로 처리한다 (공격 카드면 공격력이 합산된다).
 * discardIfNone이면 낼 수 있는 카드가 없을 때 한 장을 버려야 한다. 아니면 추가 제출 없이 끝난다.
 */
public final class ExtraPlayEffect implements EffectHandler {

    public static final Set<String> MODES = Set.of("ANY", "SUM", "DOUBLE", "HEAL_SELF");

    @Override
    public String type() {
        return "EXTRA_PLAY";
    }

    @Override
    public Set<String> params() {
        return Set.of("filter", "count", "attackMode", "discardIfNone", "noDoubleCardIds");
    }

    @Override
    public void apply(TurnContext ctx, EffectSpec spec) {
        ctx.requestExtraPlay(spec);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        if (spec.raw("filter") != null) {
            errors.addAll(check.filter(spec.raw("filter"), path + ".filter"));
        }
        if (spec.raw("count") != null && !(spec.integerParam("count") && spec.integer("count") == 1)) {
            errors.add(path + ".count: 현재는 1만 지원합니다");
        }
        if (spec.raw("attackMode") != null && !MODES.contains(spec.str("attackMode"))) {
            errors.add(path + ".attackMode: " + MODES + " 중 하나여야 합니다");
        }
        Validations.requireCardIds(spec, "noDoubleCardIds", path, check, errors);
        return errors;
    }
}
