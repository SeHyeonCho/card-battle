package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.Playability;
import com.cardbattle.engine.state.FieldLock;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.util.List;
import java.util.Map;

/**
 * 카드 제출 가능 여부 판정 (PRD 7.3의 3~7단계).
 * 1~2단계(차례·손패 확인)는 GameEngine이 먼저 한다.
 */
public final class PlayabilityChecker {

    private final CardPack pack;
    private final ConditionEvaluator conditions;

    public PlayabilityChecker(CardPack pack) {
        this.pack = pack;
        this.conditions = new ConditionEvaluator(pack);
    }

    /**
     * @param targetId 고른 대상. null이면 "고를 수 있는 대상이 하나라도 있는가"로 판정한다
     *                 (손패 표시용 PLAYABILITY_UPDATED에서 사용)
     */
    public Playability check(GameState state, PlayerState player, CardDefinition card, String targetId) {
        if (!card.alwaysPlayable()) {
            // 4. 필드 락
            FieldLock lock = fieldLock(state, card);
            if (lock != null) {
                return Playability.blocked(PlayBlockReason.FIELD_LOCK,
                        "필드의 '" + cardName(lock.getCardId()) + "' 때문에 낼 수 없습니다");
            }
            // 5. 저주 락
            if (curseLocked(player, card)) {
                return Playability.blocked(PlayBlockReason.CURSE_LOCK,
                        "걸린 저주 '" + cardName(player.getCurse().getCardId()) + "' 때문에 낼 수 없습니다");
            }
        }
        // 6. 제출 조건
        if (!conditions.testAll(card.conditions(), state, player)) {
            return Playability.blocked(PlayBlockReason.CONDITION_UNMET, "카드의 사용 조건을 만족하지 않습니다");
        }
        // 7. 대상 (멈춰! 저주가 걸려 있으면 대상은 항상 자기 자신이라 검사하지 않는다)
        if (card.targeting().requiresChoice() && !Passives.has(player, Passives.FORCE_SELF_TARGET)) {
            if (targetId == null) {
                boolean anyTarget = state.alivePlayers().stream()
                        .anyMatch(p -> p == player ? card.targeting().allowsSelf() : !untargetable(p));
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
                if (target != player && untargetable(target)) {
                    return Playability.invalidTarget("천상의 보호막 때문에 고를 수 없는 대상입니다");
                }
            }
        }
        return Playability.ok();
    }

    private static boolean untargetable(PlayerState p) {
        return p.status(Statuses.UNTARGETABLE) != null;
    }

    /** 이 카드를 막는 필드 락. 없으면 null */
    public static FieldLock fieldLock(GameState state, CardDefinition card) {
        for (FieldLock lock : state.getFieldLocks()) {
            if (CardFilter.matches(lock.getFilter(), card)) {
                return lock;
            }
        }
        return null;
    }

    /** 저주 정의의 locks(카드 필터 목록) 중 하나라도 맞으면 막힌다 */
    @SuppressWarnings("unchecked")
    public static boolean curseLocked(PlayerState player, CardDefinition card) {
        if (!player.cursed() || !(player.getCurse().getDef().get("locks") instanceof List<?> locks)) {
            return false;
        }
        for (Object f : locks) {
            if (f instanceof Map<?, ?> filter && CardFilter.matches((Map<String, Object>) filter, card)) {
                return true;
            }
        }
        return false;
    }

    private String cardName(String cardId) {
        CardDefinition def = pack.card(cardId);
        return def == null ? cardId : def.name();
    }
}
