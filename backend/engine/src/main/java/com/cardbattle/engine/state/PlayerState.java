package com.cardbattle.engine.state;

import com.cardbattle.engine.GameSettings;
import com.cardbattle.engine.card.CardInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * 플레이어 한 명의 상태. Redis에 JSON으로 저장되므로
 * 기본 생성자 + getter/setter 형태를 유지한다.
 * (계산용 메서드에는 get/is 접두사를 붙이지 않는다 — JSON 속성으로 오인되지 않도록)
 */
public class PlayerState {

    private String playerId;
    private String nickname;
    private int seat;
    private int hp;
    private int hpCap;
    private int handLimit;
    private List<CardInstance> hand = new ArrayList<>();
    private boolean eliminated;
    /** 탈락한 턴 번호. 순위 계산용 (생존 중이면 0) */
    private int eliminatedAtTurn;
    /** 연속 시간 초과 횟수 (자리 비움 판정용, PRD 4.3) */
    private int consecutiveTimeouts;
    /** 걸린 저주. 없으면 null (최대 1개, PRD 7.8) */
    private CurseState curse;
    /** 걸린 지속 상태 */
    private List<StatusState> statuses = new ArrayList<>();

    public PlayerState() {
    }

    public PlayerState(String playerId, String nickname, int seat, int hp, int hpCap, int handLimit) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.seat = seat;
        this.hp = hp;
        this.hpCap = hpCap;
        this.handLimit = handLimit;
    }

    public CardInstance findInHand(String instanceId) {
        for (CardInstance card : hand) {
            if (card.instanceId().equals(instanceId)) {
                return card;
            }
        }
        return null;
    }

    public boolean alive() {
        return !eliminated;
    }

    /** 자리 비움: 살아 있고 연속 시간 초과가 기준 이상 */
    public boolean away() {
        return !eliminated && consecutiveTimeouts >= GameSettings.AWAY_AFTER_TIMEOUTS;
    }

    public boolean cursed() {
        return curse != null;
    }

    public StatusState status(String name) {
        for (StatusState st : statuses) {
            if (st.getStatus().equals(name)) {
                return st;
            }
        }
        return null;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getSeat() {
        return seat;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getHpCap() {
        return hpCap;
    }

    public void setHpCap(int hpCap) {
        this.hpCap = hpCap;
    }

    public int getHandLimit() {
        return handLimit;
    }

    public void setHandLimit(int handLimit) {
        this.handLimit = handLimit;
    }

    public List<CardInstance> getHand() {
        return hand;
    }

    public void setHand(List<CardInstance> hand) {
        this.hand = hand == null ? new ArrayList<>() : new ArrayList<>(hand);
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public int getEliminatedAtTurn() {
        return eliminatedAtTurn;
    }

    public void setEliminatedAtTurn(int eliminatedAtTurn) {
        this.eliminatedAtTurn = eliminatedAtTurn;
    }

    public int getConsecutiveTimeouts() {
        return consecutiveTimeouts;
    }

    public void setConsecutiveTimeouts(int consecutiveTimeouts) {
        this.consecutiveTimeouts = consecutiveTimeouts;
    }

    public CurseState getCurse() {
        return curse;
    }

    public void setCurse(CurseState curse) {
        this.curse = curse;
    }

    public List<StatusState> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<StatusState> statuses) {
        this.statuses = statuses == null ? new ArrayList<>() : new ArrayList<>(statuses);
    }
}
