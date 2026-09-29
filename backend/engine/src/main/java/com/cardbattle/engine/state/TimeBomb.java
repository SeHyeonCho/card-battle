package com.cardbattle.engine.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게임에 설치된 랜덤시한폭탄 (TIME_BOMB). 매 턴 정산 5단계(맨 마지막)에서 터질지 판정하고,
 * 터지면 그 턴에 행동한 사람이 피해를 받는다. 한 번에 하나만 있고, 새로 설치하면 바뀐다.
 */
public class TimeBomb {

    private String ownerId;
    private double p;
    private int damage;
    /** 이번 턴에 이 태그의 카드를 냈으면 확률이 이 값이 된다 (예: FIRE → 0.25) */
    private Map<String, Double> pByTag = new LinkedHashMap<>();

    public TimeBomb() {
    }

    public TimeBomb(String ownerId, double p, int damage, Map<String, Double> pByTag) {
        this.ownerId = ownerId;
        this.p = p;
        this.damage = damage;
        setPByTag(pByTag);
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public double getP() {
        return p;
    }

    public void setP(double p) {
        this.p = p;
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public Map<String, Double> getPByTag() {
        return pByTag;
    }

    public void setPByTag(Map<String, Double> pByTag) {
        this.pByTag = pByTag == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pByTag);
    }
}
