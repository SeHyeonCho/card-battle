package com.cardbattle.engine.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추가 제출 진행 중인 턴 (PRD 7.2 EXTRA_PLAY). 폭풍·슈퍼파워·시간아 멈춰라!·컨슘을 내면 생기고,
 * 같은 플레이어가 한 장을 더 내거나 버리면 지금까지 낸 카드를 모아 한 번에 누적 판정·정산한다.
 * 요청 사이에 서버가 재시작돼도 이어지도록 게임 상태에 저장한다.
 */
public class ExtraPlayState {

    /** 지금까지 낸 카드 (끝나면 필드가 이 카드들로 바뀐다) */
    private List<FieldCard> played = new ArrayList<>();
    /** 누적 판정에 쓸 공격력 합 */
    private int attack;
    /** 공격 카드가 하나라도 공격력으로 반영됐는가 (아니면 비공격 판정) */
    private boolean attackPlayed;
    private boolean immune;
    /** 효과가 이미 정한 누적 판정 (ChainOutcome 이름). 없으면 null */
    private String outcome;
    /** 다음 카드 처리 방식: ANY(그대로) / SUM(공격력 합산) / DOUBLE(2배) / HEAL_SELF(공격력만큼 회복) */
    private String mode;
    /** 다음에 낼 수 있는 카드 (CardFilter). null이면 아무 카드 */
    private Map<String, Object> filter;
    /** DOUBLE에서 2배가 되지 않는 카드 (원펀치) */
    private List<String> noDoubleCardIds = new ArrayList<>();
    /** 낼 수 있는 카드가 없어 한 장을 버려야 하는 상태 (폭풍·컨슘) */
    private boolean discardOnly;
    private String lastCardId;
    private String lastInstanceId;
    private String chosenTargetId;

    public ExtraPlayState() {
    }

    public List<FieldCard> getPlayed() {
        return played;
    }

    public void setPlayed(List<FieldCard> played) {
        this.played = played == null ? new ArrayList<>() : new ArrayList<>(played);
    }

    public int getAttack() {
        return attack;
    }

    public void setAttack(int attack) {
        this.attack = attack;
    }

    public boolean isAttackPlayed() {
        return attackPlayed;
    }

    public void setAttackPlayed(boolean attackPlayed) {
        this.attackPlayed = attackPlayed;
    }

    public boolean isImmune() {
        return immune;
    }

    public void setImmune(boolean immune) {
        this.immune = immune;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Map<String, Object> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, Object> filter) {
        this.filter = filter == null ? null : new LinkedHashMap<>(filter);
    }

    public List<String> getNoDoubleCardIds() {
        return noDoubleCardIds;
    }

    public void setNoDoubleCardIds(List<String> noDoubleCardIds) {
        this.noDoubleCardIds = noDoubleCardIds == null ? new ArrayList<>() : new ArrayList<>(noDoubleCardIds);
    }

    public boolean isDiscardOnly() {
        return discardOnly;
    }

    public void setDiscardOnly(boolean discardOnly) {
        this.discardOnly = discardOnly;
    }

    public String getLastCardId() {
        return lastCardId;
    }

    public void setLastCardId(String lastCardId) {
        this.lastCardId = lastCardId;
    }

    public String getLastInstanceId() {
        return lastInstanceId;
    }

    public void setLastInstanceId(String lastInstanceId) {
        this.lastInstanceId = lastInstanceId;
    }

    public String getChosenTargetId() {
        return chosenTargetId;
    }

    public void setChosenTargetId(String chosenTargetId) {
        this.chosenTargetId = chosenTargetId;
    }
}
