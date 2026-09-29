package com.cardbattle.engine.state;

/**
 * 필드에 놓인 카드 (PRD 7.7). 카드를 제출하면 필드가 그 카드로 교체되고,
 * 버리기는 필드를 바꾸지 않는다.
 *
 * @param attack 실제로 적용된 공격력 (랜덤 공격력이면 굴린 값, 공격 카드가 아니면 0)
 */
public record FieldCard(String instanceId, String cardId, String ownerId, int attack, int playedTurn) {
}
