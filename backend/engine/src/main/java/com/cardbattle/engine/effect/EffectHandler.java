package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.List;
import java.util.Set;

/**
 * 이펙트 프리미티브 하나를 실행하는 핸들러 (Strategy 패턴, PRD 8.3).
 * 새 프리미티브를 추가하려면 이 인터페이스를 구현하고 {@link EffectRegistry}에 등록한다.
 */
public interface EffectHandler {

    /** JSON의 "type" 값 (예: "DAMAGE") */
    String type();

    void apply(TurnContext ctx, EffectSpec spec);

    /**
     * 이 효과가 받는 파라미터 이름 (type·timing·when 제외).
     * 여기 없는 키를 쓴 카드는 import에서 거부된다 — 오타나 아직 구현하지 않은 옵션이
     * 조용히 무시되어 카드가 엉뚱하게 동작하는 것을 막는다.
     */
    Set<String> params();

    /** 팩 import 시 파라미터 검증. 오류 메시지 목록을 돌려준다 */
    default List<String> validate(EffectSpec spec, String path) {
        return List.of();
    }
}
