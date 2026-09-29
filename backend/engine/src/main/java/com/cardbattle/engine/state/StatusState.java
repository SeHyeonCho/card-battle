package com.cardbattle.engine.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 플레이어에게 걸린 지속 상태 (APPLY_STATUS). 예: UNTARGETABLE(천상의 보호막), REGEN(황금사과).
 * 그 플레이어의 턴이 끝날 때마다 turnsLeft가 1 줄고, 0이 되면 사라진다 (PRD 7.6 4단계).
 */
public class StatusState {

    private String status;
    private int turnsLeft;
    /** 상태를 건 사람 */
    private String sourceId;
    private Map<String, Object> params = new LinkedHashMap<>();
    /** 걸린 턴 번호. 걸린 그 턴에는 줄지 않는다 */
    private int appliedTurn;

    public StatusState() {
    }

    public StatusState(String status, int turnsLeft, String sourceId, Map<String, Object> params, int appliedTurn) {
        this.status = status;
        this.turnsLeft = turnsLeft;
        this.sourceId = sourceId;
        setParams(params);
        this.appliedTurn = appliedTurn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTurnsLeft() {
        return turnsLeft;
    }

    public void setTurnsLeft(int turnsLeft) {
        this.turnsLeft = turnsLeft;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    public int getAppliedTurn() {
        return appliedTurn;
    }

    public void setAppliedTurn(int appliedTurn) {
        this.appliedTurn = appliedTurn;
    }
}
