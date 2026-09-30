package com.cardbattle.server.room;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 (대기실). Redis에 JSON으로 저장되고, 그대로 /topic/rooms/{roomId} 로 전송된다.
 * (계산용 메서드에는 get/is 접두사를 붙이지 않는다 — JSON 속성으로 오인되지 않도록)
 */
public class Room {

    private String roomId;
    private String inviteCode;
    private String hostId;
    private RoomSettings settings;
    private RoomStatus status = RoomStatus.LOBBY;
    private List<RoomMember> members = new ArrayList<>();
    private String gameId;
    private long createdAt;
    /** 방장이 강퇴한 사람. 이 방에는 다시 들어올 수 없다 (FR-ROOM-05, FR-GAME-07) */
    private List<String> kickedIds = new ArrayList<>();

    public RoomMember member(String playerId) {
        return members.stream().filter(m -> m.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }

    public boolean hostedBy(String playerId) {
        return hostId != null && hostId.equals(playerId);
    }

    public boolean kicked(String playerId) {
        return kickedIds.contains(playerId);
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public RoomSettings getSettings() {
        return settings;
    }

    public void setSettings(RoomSettings settings) {
        this.settings = settings;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public List<RoomMember> getMembers() {
        return members;
    }

    public void setMembers(List<RoomMember> members) {
        this.members = members == null ? new ArrayList<>() : new ArrayList<>(members);
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public List<String> getKickedIds() {
        return kickedIds;
    }

    public void setKickedIds(List<String> kickedIds) {
        this.kickedIds = kickedIds == null ? new ArrayList<>() : new ArrayList<>(kickedIds);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
