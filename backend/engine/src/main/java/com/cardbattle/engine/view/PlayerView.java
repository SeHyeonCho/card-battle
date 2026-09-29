package com.cardbattle.engine.view;

import com.cardbattle.engine.card.CardInstance;

import java.util.List;

/**
 * 모두에게 공개되는 플레이어 정보. 손패 내용은 들어가지 않는다.
 * 단, "지켜보고 있다" 저주가 걸리면 revealedHand에 손패가 공개된다 (그 외에는 null).
 *
 * @param hpCap     실제 체력 상한 (저주로 줄어든 값 포함)
 * @param handLimit 실제 손패 한도 (저주로 고정된 값 포함)
 * @param curse     걸린 저주. 없으면 null
 */
public record PlayerView(String playerId, String nickname, int seat, int hp, int hpCap, int handCount,
                         int handLimit, boolean eliminated, Curse curse, List<Status> statuses,
                         List<CardInstance> revealedHand) {

    public record Curse(String cardId, String casterId) {
    }

    public record Status(String status, int turnsLeft) {
    }
}
