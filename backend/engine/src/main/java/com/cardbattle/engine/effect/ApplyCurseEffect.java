package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.Passives;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.CurseState;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대상에게 저주를 건다 (PRD 7.8, CurseDef). 새 저주는 기존 저주를 덮어쓴다.
 * <pre>
 * { "type": "APPLY_CURSE", "target": "CHOSEN", "curse": {
 *     "id": "curse.lego",
 *     "onTurnEnd": [ { "type": "DAMAGE", "target": "CURSED", "amount": 10 } ],   저주받은 사람 턴 종료 시
 *     "locks": [ { "category": "SUPPORT" } ],                                      낼 수 없는 카드 (카드 필터)
 *     "passives": [ { "type": "HP_CAP", "value": 100 } ] } }                       지속 효과 ({@link Passives})
 * </pre>
 */
public final class ApplyCurseEffect implements EffectHandler {

    private static final Set<String> CURSE_KEYS = Set.of("id", "onTurnEnd", "locks", "passives");

    @Override
    public String type() {
        return "APPLY_CURSE";
    }

    @Override
    public Set<String> params() {
        return Set.of("target", "curse");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(TurnContext ctx, EffectSpec spec) {
        Map<String, Object> def = new LinkedHashMap<>((Map<String, Object>) spec.raw("curse"));
        String cardId = ctx.card() == null ? null : ctx.card().id();
        for (PlayerState target : ctx.resolveTargets(spec.str("target"))) {
            ctx.applyCurse(target, new CurseState((String) def.get("id"), cardId, ctx.actor().getPlayerId(), def));
        }
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        Validations.requireTarget(spec, "target", path, errors);
        String cPath = path + ".curse";
        if (!(spec.raw("curse") instanceof Map<?, ?> curse)) {
            errors.add(cPath + ": 저주 정의 객체가 필요합니다");
            return errors;
        }
        for (Object key : curse.keySet()) {
            if (!CURSE_KEYS.contains(String.valueOf(key))) {
                errors.add(cPath + "." + key + ": 알 수 없는 키입니다 (지원: " + CURSE_KEYS + ")");
            }
        }
        if (!(curse.get("id") instanceof String id) || id.isBlank()) {
            errors.add(cPath + ".id: 저주 ID가 필요합니다");
        }
        if (curse.get("onTurnEnd") != null) {
            errors.addAll(check.effects(curse.get("onTurnEnd"), cPath + ".onTurnEnd"));
        }
        if (curse.get("locks") != null) {
            if (curse.get("locks") instanceof List<?> locks) {
                for (int i = 0; i < locks.size(); i++) {
                    errors.addAll(check.filter(locks.get(i), cPath + ".locks[" + i + "]"));
                }
            } else {
                errors.add(cPath + ".locks: 카드 필터 배열이어야 합니다");
            }
        }
        if (curse.get("passives") != null) {
            if (curse.get("passives") instanceof List<?> passives) {
                for (int i = 0; i < passives.size(); i++) {
                    errors.addAll(validatePassive(passives.get(i), cPath + ".passives[" + i + "]"));
                }
            } else {
                errors.add(cPath + ".passives: 배열이어야 합니다");
            }
        }
        return errors;
    }

    private static List<String> validatePassive(Object raw, String path) {
        List<String> errors = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> p)) {
            errors.add(path + ": 객체여야 합니다");
            return errors;
        }
        Object type = p.get("type");
        Set<String> allowed = Passives.PARAMS.get(String.valueOf(type));
        if (allowed == null) {
            errors.add(path + ".type: 알 수 없는 지속 효과 '" + type + "' (지원: " + Passives.PARAMS.keySet() + ")");
            return errors;
        }
        for (Object key : p.keySet()) {
            if (!"type".equals(key) && !allowed.contains(String.valueOf(key))) {
                errors.add(path + "." + key + ": " + type + " 에서 쓸 수 없는 키입니다 (지원: " + allowed + ")");
            }
        }
        for (String key : allowed) {
            if (Set.of("value", "seconds", "p").contains(key) && !(p.get(key) instanceof Number)) {
                errors.add(path + "." + key + ": 숫자가 필요합니다");
            }
        }
        return errors;
    }
}
