package com.cardbattle.server.room;

public class RoomMember {

    private String playerId;
    private String nickname;
    private boolean ready;
    private long joinedAt;

    public RoomMember() {
    }

    public RoomMember(String playerId, String nickname, long joinedAt) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.joinedAt = joinedAt;
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

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }
}
