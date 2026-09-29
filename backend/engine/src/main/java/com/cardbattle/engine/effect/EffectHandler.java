package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.rules.TurnContext;

import java.util.List;

/**
 * 이펙트 프리미티브 하나를 실행하는 핸들러 (Strategy 패턴, PRD 8.3).
 * 새 프리미티브를 추가하려면 이 인터페이스를 구현하고 {@link EffectRegistry}에 등록한다.
 */
public interface EffectHandler {

    /** JSON의 "type" 값 (예: "DAMAGE") */
    String type();

    void apply(TurnContext ctx, EffectSpec spec);

    /** 팩 import 시 파라미터 검증. 오류 메시지 목록을 돌려준다 */
    default List<String> validate(EffectSpec spec, String path) {
        return List.of();
    }
}
