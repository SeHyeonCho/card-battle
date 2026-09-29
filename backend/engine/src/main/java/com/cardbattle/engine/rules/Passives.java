package com.cardbattle.engine.rules;

import com.cardbattle.engine.state.PlayerState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 저주에 딸린 지속 효과(passives)를 읽는다. 저주 정의 예:
 * <pre>{ "id": "curse.crisis", "passives": [ { "type": "HP_CAP", "value": 100 } ] }</pre>
 */
public final class Passives {

    public static final String REVEAL_HAND = "REVEAL_HAND";
    public static final String EXTRA_RECEIVE_DAMAGE = "EXTRA_RECEIVE_DAMAGE";
    public static final String HP_CAP = "HP_CAP";
    public static final String HEAL_MULTIPLIER = "HEAL_MULTIPLIER";
    public static final String REDIRECT_CASTER_RECEIVE = "REDIRECT_CASTER_RECEIVE";
    public static final String HAND_LIMIT_FIXED = "HAND_LIMIT_FIXED";
    public static final String PLAY_BECOMES_DISCARD = "PLAY_BECOMES_DISCARD";
    public static final String FORCE_SELF_TARGET = "FORCE_SELF_TARGET";
    public static final String TURN_TIME = "TURN_TIME";
    public static final String STRIP_ATTACK_EFFECTS = "STRIP_ATTACK_EFFECTS";

    /** 타입별 허용 파라미터 (팩 검증용) */
    public static final Map<String, Set<String>> PARAMS = Map.of(
            REVEAL_HAND, Set.of(),
            EXTRA_RECEIVE_DAMAGE, Set.of("value"),
            HP_CAP, Set.of("value"),
            HEAL_MULTIPLIER, Set.of("value", "exceptCategories"),
            REDIRECT_CASTER_RECEIVE, Set.of("onlyWhenAttackCard"),
            HAND_LIMIT_FIXED, Set.of("value"),
            PLAY_BECOMES_DISCARD, Set.of("p", "exceptAlwaysPlayable"),
            FORCE_SELF_TARGET, Set.of(),
            TURN_TIME, Set.of("seconds"),
            STRIP_ATTACK_EFFECTS, Set.of());

    private Passives() {
    }

    /** 이 플레이어의 저주에 해당 타입 지속 효과가 있으면 그 설정, 없으면 null */
    public static Map<?, ?> find(PlayerState p, String type) {
        if (p == null || !p.cursed() || !(p.getCurse().getDef().get("passives") instanceof List<?> list)) {
            return null;
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && type.equals(m.get("type"))) {
                return m;
            }
        }
        return null;
    }

    public static boolean has(PlayerState p, String type) {
        return find(p, type) != null;
    }

    public static int number(Map<?, ?> passive, String key, int defaultValue) {
        return passive != null && passive.get(key) instanceof Number n ? n.intValue() : defaultValue;
    }

    public static double decimal(Map<?, ?> passive, String key, double defaultValue) {
        return passive != null && passive.get(key) instanceof Number n ? n.doubleValue() : defaultValue;
    }

    /** 실제 체력 상한: 기본 상한과 저주(위기상황 재현) 중 작은 값 */
    public static int hpCap(PlayerState p) {
        Map<?, ?> cap = find(p, HP_CAP);
        return cap == null ? p.getHpCap() : Math.min(p.getHpCap(), number(cap, "value", p.getHpCap()));
    }

    /** 실제 손패 한도: 저주(햄보칼수업서)가 있으면 그 값으로 고정 */
    public static int handLimit(PlayerState p) {
        Map<?, ?> fixed = find(p, HAND_LIMIT_FIXED);
        return fixed == null ? p.getHandLimit() : number(fixed, "value", p.getHandLimit());
    }

    /** 이 플레이어의 턴 제한 시간(초): 저주(초읽기)가 있으면 더 짧은 쪽 */
    public static int turnSeconds(PlayerState p, int defaultSeconds) {
        Map<?, ?> t = find(p, TURN_TIME);
        return t == null ? defaultSeconds : Math.min(defaultSeconds, number(t, "seconds", defaultSeconds));
    }
}
