package com.cardbattle.engine.rules;

import java.util.Set;

/** 지속 상태 이름 (APPLY_STATUS / DISPEL_STATUSES). */
public final class Statuses {

    /** 다른 사람이 대상으로 고를 수 없다 (천상의 보호막) */
    public static final String UNTARGETABLE = "UNTARGETABLE";
    /** 내 턴이 끝날 때 params.amount 만큼 회복 (황금사과) */
    public static final String REGEN = "REGEN";
    /** 다음 공격 카드 공격력 + params.value (스팀팩) */
    public static final String NEXT_ATTACK_BONUS = "NEXT_ATTACK_BONUS";
    /** 게임 전체에 걸리는 것: 랜덤시한폭탄, 무승부 카운트다운 (DISPEL_STATUSES로 없앨 수 있다) */
    public static final String TIME_BOMB = "TIME_BOMB";
    public static final String DRAW_COUNTDOWN = "DRAW_COUNTDOWN";

    public static final Set<String> PLAYER = Set.of(UNTARGETABLE, REGEN, NEXT_ATTACK_BONUS);
    public static final Set<String> ALL = Set.of(UNTARGETABLE, REGEN, NEXT_ATTACK_BONUS, TIME_BOMB, DRAW_COUNTDOWN);

    private Statuses() {
    }
}
