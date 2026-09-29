package com.cardbattle.engine.card;

/**
 * 손패에 들어 있는 카드 한 장. 같은 카드가 여러 장 나올 수 있으므로
 * 카드 종류(cardId)와 별개로 고유한 instanceId를 가진다.
 */
public record CardInstance(String instanceId, String cardId) {
}
