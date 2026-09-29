package com.cardbattle.engine.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 필드 카드가 거는 제출 제한 (FIELD_LOCK, PRD 7.7).
 * 필드가 다른 카드로 바뀌면 사라지고, 바뀌지 않아도 expiresAfterTurn 턴이 끝나면 풀린다 (최대 2턴).
 */
public class FieldLock {

    /** 락을 건 필드 카드 */
    private String sourceInstanceId;
    private String cardId;
    private String ownerId;
    /** 낼 수 없는 카드 (CardFilter) */
    private Map<String, Object> filter = new LinkedHashMap<>();
    private int expiresAfterTurn;

    public FieldLock() {
    }

    public FieldLock(String sourceInstanceId, String cardId, String ownerId, Map<String, Object> filter,
                     int expiresAfterTurn) {
        this.sourceInstanceId = sourceInstanceId;
        this.cardId = cardId;
        this.ownerId = ownerId;
        setFilter(filter);
        this.expiresAfterTurn = expiresAfterTurn;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Map<String, Object> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, Object> filter) {
        this.filter = filter == null ? new LinkedHashMap<>() : new LinkedHashMap<>(filter);
    }

    public int getExpiresAfterTurn() {
        return expiresAfterTurn;
    }

    public void setExpiresAfterTurn(int expiresAfterTurn) {
        this.expiresAfterTurn = expiresAfterTurn;
    }
}
