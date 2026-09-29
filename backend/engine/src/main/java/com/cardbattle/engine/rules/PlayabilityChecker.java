package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.Playability;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

/**
 * 카드 제출 가능 여부 판정 (PRD 7.3의 3~7단계).
 * 1~2단계(차례·손패 확인)는 GameEngine이 먼저 한다.
 */
public final class PlayabilityChecker {

    private final ConditionEvaluator conditions;

    public PlayabilityChecker(CardPack pack) {
        this.conditions = new ConditionEvaluator(pack);
    }

    /**
     * @param targetId 고른 대상. null이면 "고를 수 있는 대상이 하나라도 있는가"로 판정한다
     *                 (손패 표시용 PLAYABILITY_UPDATED에서 사용)
     */
    public Playability check(GameState state, PlayerState player, CardDefinition card, String targetId) {
        if (!card.alwaysPlayable()) {
            // 4. 필드 락 — Phase 2 (FIELD_LOCK 효과)에서 구현
            // 5. 저주 락 — Phase 2 (APPLY_CURSE 효과)에서 구현
        }
        // 6. 제출 조건
        if (!conditions.testAll(card.conditions(), state, player)) {
            return Playability.blocked(PlayBlockReason.CONDITION_UNMET, "카드의 사용 조건을 만족하지 않습니다");
        }
        // 7. 대상
        if (card.targeting().requiresChoice()) {
            if (targetId == null) {
                boolean anyTarget = state.alivePlayers().stream()
                        .anyMatch(p -> card.targeting().allowsSelf() || p != player);
                if (!anyTarget) {
                    return Playability.invalidTarget("고를 수 있는 대상이 없습니다");
                }
            } else {
                PlayerState target = state.player(targetId);
                if (target == null || !target.alive()) {
                    return Playability.invalidTarget("대상이 없거나 이미 탈락했습니다");
                }
                if (target == player && !card.targeting().allowsSelf()) {
                    return Playability.invalidTarget("자기 자신은 고를 수 없습니다");
                }
            }
        }
        return Playability.ok();
    }
}
