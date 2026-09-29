package com.cardbattle.engine.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 플레이어에게 걸린 저주 (PRD 7.8, CurseDef). 한 사람에게 최대 1개이며 새 저주가 덮어쓴다.
 * Redis에 JSON으로 저장되므로 기본 생성자 + getter/setter 형태를 유지한다.
 */
public class CurseState {

    /** 저주 정의 ID (예: curse.lego) */
    private String curseId;
    /** 저주를 건 카드 */
    private String cardId;
    /** 저주를 건 사람. 이 사람이 탈락하면 저주가 풀린다 */
    private String casterId;
    /** 카드에 적힌 저주 정의 원본 (onTurnEnd, locks, passives) */
    private Map<String, Object> def = new LinkedHashMap<>();

    public CurseState() {
    }

    public CurseState(String curseId, String cardId, String casterId, Map<String, Object> def) {
        this.curseId = curseId;
        this.cardId = cardId;
        this.casterId = casterId;
        setDef(def);
    }

    public String getCurseId() {
        return curseId;
    }

    public void setCurseId(String curseId) {
        this.curseId = curseId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCasterId() {
        return casterId;
    }

    public void setCasterId(String casterId) {
        this.casterId = casterId;
    }

    public Map<String, Object> getDef() {
        return def;
    }

    public void setDef(Map<String, Object> def) {
        this.def = def == null ? new LinkedHashMap<>() : new LinkedHashMap<>(def);
    }
}
