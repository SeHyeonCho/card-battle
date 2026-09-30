package com.cardbattle.server.room;

import com.cardbattle.engine.GameSettings;
import com.cardbattle.server.common.ApiException;

import java.util.Set;

/** 방 설정 (FR-ROOM-01). 범위를 벗어난 값은 방을 만들거나 바꿀 때 거부한다 (FR-ROOM-05). */
public record RoomSettings(int maxPlayers, String packCode, int startingHp, int hpCap, int handSize,
                           int turnTimeSeconds) {

    private static final Set<Integer> TURN_TIMES = Set.of(15, 25, 40);

    /** 클라이언트 요청. 비운 값은 기본값을 쓴다 */
    public record Request(Integer maxPlayers, String packCode, Integer startingHp, Integer hpCap, Integer handSize,
                          Integer turnTimeSeconds) {
    }

    public static RoomSettings from(Request r, String defaultPackCode) {
        return new RoomSettings(4, defaultPackCode, 200, 500, 5, 25).merge(r);
    }

    /** 요청에 있는 값만 바꾼 새 설정 (대기실에서 설정을 바꿀 때, FR-ROOM-05). 비운 값은 지금 값을 그대로 쓴다 */
    public RoomSettings merge(Request r) {
        if (r == null) {
            throw ApiException.badRequest("INVALID_SETTINGS", "바꿀 설정이 없습니다");
        }
        RoomSettings s = new RoomSettings(
                r.maxPlayers() == null ? maxPlayers : r.maxPlayers(),
                r.packCode() == null || r.packCode().isBlank() ? packCode : r.packCode(),
                r.startingHp() == null ? startingHp : r.startingHp(),
                r.hpCap() == null ? hpCap : r.hpCap(),
                r.handSize() == null ? handSize : r.handSize(),
                r.turnTimeSeconds() == null ? turnTimeSeconds : r.turnTimeSeconds());
        s.validate();
        return s;
    }

    private void validate() {
        if (maxPlayers < GameSettings.MIN_PLAYERS || maxPlayers > GameSettings.MAX_PLAYERS) {
            throw ApiException.badRequest("INVALID_SETTINGS", "최대 인원은 2~6명입니다");
        }
        if (startingHp < 100 || startingHp > 1000) {
            throw ApiException.badRequest("INVALID_SETTINGS", "시작 체력은 100~1000입니다");
        }
        if (hpCap < startingHp) {
            throw ApiException.badRequest("INVALID_SETTINGS", "체력 상한은 시작 체력 이상이어야 합니다");
        }
        if (handSize < 4 || handSize > 7) {
            throw ApiException.badRequest("INVALID_SETTINGS", "손패 수는 4~7장입니다");
        }
        if (!TURN_TIMES.contains(turnTimeSeconds)) {
            throw ApiException.badRequest("INVALID_SETTINGS", "턴 제한 시간은 15 / 25 / 40초 중 하나입니다");
        }
    }

    public GameSettings toGameSettings() {
        return new GameSettings(startingHp, hpCap, handSize, turnTimeSeconds, "SC1");
    }
}
