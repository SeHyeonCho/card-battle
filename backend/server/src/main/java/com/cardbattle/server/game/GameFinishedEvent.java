package com.cardbattle.server.game;

/** 게임이 끝났다는 서버 내부 이벤트. RoomService가 받아서 방을 대기실로 되돌린다. */
public record GameFinishedEvent(String roomId, String gameId) {
}
